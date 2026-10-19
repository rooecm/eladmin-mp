package fun.rooe.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.SecurityErrorResponseWriter;
import fun.rooe.admin.framework.security.TokenAuthenticationFilter;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.framework.security.model.UserSnapshot;
import fun.rooe.admin.framework.security.service.TokenService;
import fun.rooe.admin.infrastructure.security.RedisAuthCache;
import fun.rooe.admin.infrastructure.security.RedisSessionService;
import fun.rooe.admin.modules.security.domain.dto.RolePermissions;
import fun.rooe.admin.modules.security.service.AuthReadService;
import fun.rooe.admin.modules.security.service.impl.AuthIdentityServiceImpl;
import fun.rooe.admin.modules.security.service.impl.RolePermissionServiceImpl;
import fun.rooe.admin.modules.security.service.impl.UserCacheServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "AUTH_TEST_REDIS_URL", matches = "redis(s)?://.+")
class AuthCacheIntegrationTest {
    static RedissonClient client;
    final long uid = 918231L, rid = 918232L;
    StringRedisTemplate redis;
    RedisAuthCache cache;
    AuthReadService reads;
    UserCacheServiceImpl users;
    RolePermissionServiceImpl roles;
    AuthProperties properties = new AuthProperties();
    ObjectMapper json = new ObjectMapper();
    AuthTelemetry telemetry = mock(AuthTelemetry.class);

    @BeforeAll
    static void connect() {
        Config config = new Config();
        config.useSingleServer().setAddress(System.getenv("AUTH_TEST_REDIS_URL"))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(8);
        client = Redisson.create(config);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.shutdown();
    }

    @BeforeEach
    void setup() {
        redis = spy(new StringRedisTemplate(new RedissonConnectionFactory(client)));
        cache = spy(new RedisAuthCache(redis, Clock.systemUTC(), telemetry));
        reads = mock(AuthReadService.class);
        users = new UserCacheServiceImpl(cache, reads, properties, json, telemetry);
        roles = new RolePermissionServiceImpl(cache, reads, properties, json);
        redis.delete(List.of(RedisAuthCache.USER + uid, RedisAuthCache.ROLE + rid));
    }

    @AfterEach
    void cleanup() {
        redis.delete(List.of(RedisAuthCache.USER + uid, RedisAuthCache.ROLE + rid));
    }

    private UserSnapshot user(long version) {
        return new UserSnapshot(uid, "user", "User", true, false, version, List.of(rid));
    }

    @Test
    void warmAuthenticationUsesOneLuaOneGetOneMgetAndZeroDatabaseQueries() throws Exception {
        var tokens = new TokenService();
        String token = tokens.generateToken(), digest = tokens.digest(token);
        var sessions = new RedisSessionService(redis, properties, Clock.systemUTC(), telemetry);
        sessions.create(digest, tokens.digest(tokens.generateToken()), uid, 2, "WEB", "127.0.0.1", "test");
        try {
            sessions.authenticate(digest); // Warm Redis' script cache before measuring the ordinary path.
            cache.fill(RedisAuthCache.USER + uid, json.writeValueAsString(user(2)), System.currentTimeMillis() + 60000);
            cache.fill(RedisAuthCache.ROLE + rid, json.writeValueAsString(new RolePermissions("USER", true, Set.of("user:list"))), System.currentTimeMillis() + 300000);
            clearInvocations(redis, cache);
            var filter = new TokenAuthenticationFilter(tokens, sessions, new AuthIdentityServiceImpl(users, roles), properties,
                    new SecurityErrorResponseWriter(json, telemetry), telemetry);
            var request = new MockHttpServletRequest("GET", "/api/users");
            request.setServletPath("/api/users");
            request.addHeader("Authorization", "Bearer " + token);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                assertNotNull(auth);
                assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("user:list")));
            });
            assertEquals(200, response.getStatus());
            assertNull(response.getHeader("X-Session-Expires-In"));
            verify(cache, times(1)).get(RedisAuthCache.USER + uid);
            verify(cache, times(1)).multiGet(List.of(RedisAuthCache.ROLE + rid));
            long scripts = mockingDetails(redis).getInvocations().stream().filter(i -> i.getMethod().getName().equals("execute")
                    && i.getArguments().length > 0 && i.getArguments()[0] instanceof org.springframework.data.redis.core.script.RedisScript).count();
            assertEquals(1, scripts);
            verifyNoInteractions(reads);
        } finally {
            sessions.revokeByAccessDigest(digest);
        }
    }

    @Test
    void staleVersionRepairsFromPrimaryAndConcurrentMissesLoadOnce() throws Exception {
        cache.fill(RedisAuthCache.USER + uid, json.writeValueAsString(user(1)), System.currentTimeMillis() + 60000);
        when(reads.user(uid)).thenReturn(user(2));
        assertEquals(2, users.getUserForSession(uid, 2).getAuthVersion());
        verify(cache).refresh(eq(RedisAuthCache.USER + uid), anyString(), anyString(), anyLong());
        verify(reads, times(1)).user(uid);
        redis.delete(RedisAuthCache.USER + uid);
        clearInvocations(reads);
        when(reads.user(uid)).thenAnswer(invocation -> {
            Thread.sleep(30);
            return user(2);
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<UserSnapshot>> pending = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                pending.add(pool.submit(() -> users.getUserForSession(uid, 2)));
            }
            for (var future : pending) {
                assertEquals(2, future.get(10, TimeUnit.SECONDS).getAuthVersion());
            }
        } finally {
            pool.shutdownNow();
        }
        verify(reads, times(1)).user(uid);
    }

    @Test
    void cacheOutageFallsBackAndFillDeadlinesAreChosenBeforeDatabaseRead() {
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline")).when(cache).get(anyString());
        when(reads.user(uid)).thenAnswer(invocation -> {
            verify(cache).deadline(properties.getCache().getUserTtl());
            return user(2);
        });
        assertEquals(2, users.getUserForSession(uid, 2).getAuthVersion());
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline")).when(cache).multiGet(anyList());
        when(reads.roles(List.of(rid))).thenAnswer(invocation -> {
            verify(cache).deadline(properties.getCache().getRolePermissionTtl());
            return Map.of(rid, new RolePermissions("USER", true, Set.of("user:list")));
        });
        assertTrue(roles.authorities(List.of(rid)).stream().anyMatch(a -> a.getAuthority().equals("user:list")));
    }
}


