package fun.rooe.admin.infrastructure.security;

import fun.rooe.admin.framework.config.security.config.AuthProperties;
import fun.rooe.admin.framework.security.AuthTelemetry;
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
        keys.add(RedisSessionService.PREFIX + value);
        return value;
    }

    private RedisSessionService sessions(Clock clock) {
        return new RedisSessionService(redis, properties, clock, telemetry);
    }

    @Test
    void opaqueTokenAndAtomicSessionLifecycle() {
        String token = tokens.generateToken();
        assertTrue(tokens.isValid(token));
        assertEquals(64, tokens.digest(token).length());
        String digest = tokens.digest(token);
        keys.add(RedisSessionService.PREFIX + digest);
        var service = sessions(Clock.systemUTC());
        var created = service.create(digest, 42, 7, "WEB", "127.0.0.1", "agent");
        var fields = redis.opsForHash().entries(RedisSessionService.PREFIX + digest);
        assertEquals("7", fields.get("authVersion"));
        assertFalse(fields.toString().contains(token));
        assertTrue(redis.getExpire(RedisSessionService.PREFIX + digest) > 0);
        var renewed = service.authenticateAndRenew(digest, true);
        assertEquals(created.getSessionId(), renewed.getSessionId());
        assertTrue(renewed.isRenewed());
        service.revokeByDigest(digest);
        assertNull(service.authenticateAndRenew(digest, true));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.PREFIX + digest)));
    }

    @Test
    void renewalClampsToAbsoluteExpiryAndExcludedRequestsDoNotExtendTtl() {
        Instant now = Instant.now();
        String digest = digest();
        var created = sessions(Clock.fixed(now, ZoneOffset.UTC)).create(digest, 1, 0, "", "", "");
        redis.expire(RedisSessionService.PREFIX + digest, Duration.ofSeconds(12));
        var excluded = sessions(Clock.fixed(now, ZoneOffset.UTC)).authenticateAndRenew(digest, false);
        assertFalse(excluded.isRenewed());
        assertTrue(redis.getExpire(RedisSessionService.PREFIX + digest) <= 12);
        var nearExpiry = sessions(Clock.fixed(Instant.ofEpochSecond(created.getAbsoluteExpireAt() - 5), ZoneOffset.UTC));
        assertEquals(5, nearExpiry.authenticateAndRenew(digest, true).getTtlSeconds());
        assertTrue(redis.getExpire(RedisSessionService.PREFIX + digest) <= 5);
        var expired = sessions(Clock.fixed(Instant.ofEpochSecond(created.getAbsoluteExpireAt()), ZoneOffset.UTC));
        assertNull(expired.authenticateAndRenew(digest, true));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.PREFIX + digest)));
    }

    @Test
    void idleExpiryIsEnforcedByRedis() throws Exception {
        properties.getSession().setIdleTimeout(Duration.ofSeconds(1));
        var service = sessions(Clock.systemUTC());
        String digest = digest();
        service.create(digest, 1, 0, "", "", "");
        Thread.sleep(1100);
        assertNull(service.authenticateAndRenew(digest, true));
    }

    @Test
    void concurrentRenewalAndLogoutNeverResurrectDeletedSession() throws Exception {
        var service = sessions(Clock.systemUTC());
        for (int round = 0; round < 10; round++) {
            String digest = digest();
            service.create(digest, 1, 0, "", "", "");
            race(40, i -> {
                if (i == 20) service.revokeByDigest(digest);
                else service.authenticateAndRenew(digest, true);
            });
            assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisSessionService.PREFIX + digest)));
            assertNull(service.authenticateAndRenew(digest, true));
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
