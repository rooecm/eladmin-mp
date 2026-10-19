package fun.rooe.admin.infrastructure.security;

import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.framework.security.service.RefreshReplayException;
import fun.rooe.admin.framework.security.service.TokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Runs against an actual Redis 6.2+ instance; never substitutes a mocked RedisTemplate.
 */
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_REDIS_URL", matches = "redis(s)?://.+")
class RedisSecurityIntegrationTest {
    private static RedissonClient client;
    private static StringRedisTemplate redis;
    private final Set<String> keys = ConcurrentHashMap.newKeySet();
    private final AuthProperties properties = new AuthProperties();
    private final TokenService tokens = new TokenService();
    private final AuthTelemetry telemetry = mock(AuthTelemetry.class);

    @BeforeAll
    static void connect() {
        Config config = new Config();
        config.useSingleServer().setAddress(System.getenv("AUTH_TEST_REDIS_URL"))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(16);
        client = Redisson.create(config);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(client));
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.shutdown();
    }

    @AfterEach
    void cleanup() {
        if (!keys.isEmpty()) redis.delete(keys);
    }

    private String digest() {
        String value = tokens.digest(tokens.generateToken());
        return value;
    }

    private RedisSessionService sessions(Clock clock) {
        return new RedisSessionService(redis, properties, clock, telemetry);
    }

    private RedisSessionService sessions() {
        return sessions(Clock.systemUTC());
    }

    /**
     * Creates a session and records every derived key so the test can clean up after itself.
     */
    private Session create(RedisSessionService service, String access, String refresh, long userId, long version) {
        var info = service.create(access, refresh, userId, version, "WEB", "127.0.0.1", "agent");
        keys.add(RedisSessionService.ACCESS_PREFIX + access);
        keys.add(RedisSessionService.REFRESH_PREFIX + refresh);
        keys.add(RedisSessionService.SESSION_PREFIX + info.getSessionId());
        return new Session(info.getSessionId(), access, refresh);
    }

    private record Session(String sessionId, String accessDigest, String refreshDigest) {
    }

    @Test
    void opaqueTokensAndAccessCredentialAreIndependentlyShortLived() {
        String token = tokens.generateToken();
        assertTrue(tokens.isValid(token));
        assertEquals(64, tokens.digest(token).length());
        var service = sessions();
        var session = create(service, digest(), digest(), 42, 7);
        var fields = redis.opsForHash().entries(RedisSessionService.SESSION_PREFIX + session.sessionId());
        assertEquals("7", fields.get("authVersion"));
        assertEquals("42", fields.get("userId"));
        assertFalse(fields.toString().contains(token));
        // Access TTL is fixed and must not be extended by repeated authentication.
        long ttl = redis.getExpire(RedisSessionService.ACCESS_PREFIX + session.accessDigest());
        assertTrue(ttl > 0 && ttl <= properties.getSession().getAccessTtl().toSeconds());
        assertNotNull(service.authenticate(session.accessDigest()));
        long after = redis.getExpire(RedisSessionService.ACCESS_PREFIX + session.accessDigest());
        assertTrue(after <= ttl);
    }

    @Test
    void refreshRotatesBothCredentialsAndRejectsTheRetiredOnes() {
        var service = sessions();
        var session = create(service, digest(), digest(), 1, 0);
        String newAccess = digest(), newRefresh = digest();
        keys.add(RedisSessionService.ACCESS_PREFIX + newAccess);
        keys.add(RedisSessionService.REFRESH_PREFIX + newRefresh);
        keys.add(RedisSessionService.REPLAY_PREFIX + session.refreshDigest());
        var rotated = service.refresh(session.refreshDigest(), newAccess, newRefresh);
        assertNotNull(rotated);
        keys.add(RedisSessionService.SESSION_PREFIX + rotated.getSessionId());
        // Old credentials are retired immediately; the new ones work.
        assertNull(service.authenticate(session.accessDigest()));
        assertNotNull(service.authenticate(newAccess));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.REFRESH_PREFIX + session.refreshDigest())));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.SESSION_PREFIX + session.sessionId())));
    }

    @Test
    void replayedRefreshRevokesTheWholeSession() {
        var service = sessions();
        var session = create(service, digest(), digest(), 1, 0);
        String newAccess = digest(), newRefresh = digest();
        keys.add(RedisSessionService.ACCESS_PREFIX + newAccess);
        keys.add(RedisSessionService.REFRESH_PREFIX + newRefresh);
        keys.add(RedisSessionService.REPLAY_PREFIX + session.refreshDigest());
        var rotated = service.refresh(session.refreshDigest(), newAccess, newRefresh);
        keys.add(RedisSessionService.SESSION_PREFIX + rotated.getSessionId());
        // Presenting the retired credential again must be treated as a leak, not as a plain expiry.
        assertThrows(RefreshReplayException.class, () -> service.refresh(session.refreshDigest(), digest(), digest()));
        service.revokeBySessionId(rotated.getSessionId());
        assertNull(service.authenticate(newAccess));
    }

    @Test
    void concurrentRefreshSucceedsExactlyOnce() throws Exception {
        for (int round = 0; round < 5; round++) {
            var service = sessions();
            var session = create(service, digest(), digest(), 1, 0);
            int attempts = 8;
            List<String> accesses = new ArrayList<>(), refreshes = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                accesses.add(digest());
                refreshes.add(digest());
                keys.add(RedisSessionService.ACCESS_PREFIX + accesses.get(i));
                keys.add(RedisSessionService.REFRESH_PREFIX + refreshes.get(i));
            }
            keys.add(RedisSessionService.REPLAY_PREFIX + session.refreshDigest());
            var successes = new java.util.concurrent.atomic.AtomicInteger();
            race(attempts, i -> {
                try {
                    var rotated = service.refresh(session.refreshDigest(), accesses.get(i), refreshes.get(i));
                    if (rotated != null) {
                        successes.incrementAndGet();
                        keys.add(RedisSessionService.SESSION_PREFIX + rotated.getSessionId());
                    }
                } catch (RefreshReplayException e) {
                    // 败者可能观测到重放标记，这属于轮换已发生的正常结果。
                } catch (org.springframework.dao.DataAccessException e) {
                    // 败者也可能撞上键已删除的瞬态，同样不计入成功。
                }
            });
            // Rotation is atomic: exactly one attempt wins, the rest fail without leaving a usable credential.
            assertEquals(1, successes.get(), "round " + round);
        }
    }

    @Test
    void refreshCannotExceedTheAbsoluteLifetime() {
        Instant now = Instant.now();
        properties.getSession().setRefreshTtl(Duration.ofSeconds(60));
        properties.getSession().setAccessTtl(Duration.ofSeconds(10));
        var service = sessions(Clock.fixed(now, ZoneOffset.UTC));
        var session = create(service, digest(), digest(), 1, 0);
        var expired = sessions(Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC));
        assertNull(expired.refresh(session.refreshDigest(), digest(), digest()));
        assertNull(expired.authenticate(session.accessDigest()));
    }

    @Test
    void expiredAccessCredentialIsNotResurrectedByRedisTiming() throws Exception {
        properties.getSession().setAccessTtl(Duration.ofSeconds(1));
        properties.getSession().setRefreshTtl(Duration.ofSeconds(60));
        var service = sessions();
        var session = create(service, digest(), digest(), 1, 0);
        Thread.sleep(1100);
        assertNull(service.authenticate(session.accessDigest()));
        // The refresh credential outlives the access credential and can still rotate.
        assertNotNull(service.refresh(session.refreshDigest(), digest(), digest()));
    }

    @Test
    void revocationRemovesBothCredentialsOfASession() {
        var service = sessions();
        var session = create(service, digest(), digest(), 1, 0);
        service.revokeByAccessDigest(session.accessDigest());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.ACCESS_PREFIX + session.accessDigest())));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.REFRESH_PREFIX + session.refreshDigest())));
        assertNull(service.authenticate(session.accessDigest()));
    }

    @Test
    void concurrentRevocationAndRefreshNeverLeaveUsableCredentials() throws Exception {
        var service = sessions();
        for (int round = 0; round < 10; round++) {
            var session = create(service, digest(), digest(), 1, 0);
            String newAccess = digest(), newRefresh = digest();
            keys.add(RedisSessionService.ACCESS_PREFIX + newAccess);
            keys.add(RedisSessionService.REFRESH_PREFIX + newRefresh);
            keys.add(RedisSessionService.REPLAY_PREFIX + session.refreshDigest());
            race(40, i -> {
                try {
                    if (i == 20) {
                        service.revokeBySessionId(session.sessionId());
                    } else {
                        var rotated = service.refresh(session.refreshDigest(), newAccess, newRefresh);
                        if (rotated != null) keys.add(RedisSessionService.SESSION_PREFIX + rotated.getSessionId());
                    }
                } catch (RefreshReplayException e) {
                    // 并发下重放标记属于预期结果。
                } catch (org.springframework.dao.DataAccessException e) {
                    // 与吊销并发时键可能已被删除，属于预期竞态。
                }
            });
            assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.ACCESS_PREFIX + session.accessDigest())));
            assertNull(service.authenticate(session.accessDigest()));
            // 原 Refresh 已被吊销；再次使用要么失败，要么被判定为重放。
            assertThrows(RefreshReplayException.class, () -> service.refresh(session.refreshDigest(), digest(), digest()));
        }
    }

    @Test
    void cacheFillAndCompareRefreshPreserveNewerValuesAndOriginalDeadlines() throws Exception {
        var cache = new RedisAuthCache(redis, Clock.systemUTC(), telemetry);
        String key = RedisAuthCache.USER + UUID.randomUUID();
        keys.add(key);
        long deadline = System.currentTimeMillis() + 1500;
        cache.fill(key, "old", deadline);
        cache.fill(key, "overwritten", deadline + 10000);
        assertEquals("old", cache.get(key));
        cache.refresh(key, "old", "new", deadline);
        cache.refresh(key, "old", "stale", deadline + 10000);
        assertEquals("new", cache.get(key));
        redis.delete(key);
        Thread.sleep(200);
        cache.fill(key, "late-old", deadline);
        long beforeTtlRead = System.currentTimeMillis();
        assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) <= deadline - beforeTtlRead);
        Thread.sleep(1400);
        assertNull(cache.get(key));
        cache.fill(key, "too-late", deadline);
        assertNull(cache.get(key));
    }

    private void race(int n, java.util.function.IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int j = i;
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        action.accept(j);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
