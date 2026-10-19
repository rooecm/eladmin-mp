package fun.rooe.admin.infrastructure.security;

import fun.rooe.admin.framework.config.security.config.AuthProperties;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.model.SessionInfo;
import fun.rooe.admin.framework.security.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Redis 会话实现：只按 Token 摘要保存会话，创建、读取及续期由 Lua 保证原子性。
 */
@Service
@RequiredArgsConstructor
public class RedisSessionService implements SessionService {
    public static final String PREFIX = "rooe:auth:session:";
    private static final DefaultRedisScript<Long> CREATE = RedisScripts.load("security/session-create.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> AUTHENTICATE = RedisScripts.load("security/session-authenticate.lua", List.class);
    private final StringRedisTemplate redis;
    private final AuthProperties properties;
    private final Clock clock;
    private final AuthTelemetry telemetry;

    @Override
    public SessionInfo create(String digest, long userId, long authVersion, String clientType, String ip, String userAgent) {
        long now = clock.instant().getEpochSecond();
        long end = now + properties.getSession().getMaxLifetime().toSeconds();
        // 初始 TTL 同时受空闲时长与绝对到期时间约束。
        long ttl = Math.min(properties.getSession().getIdleTimeout().toSeconds(), end - now);
        String sessionId = UUID.randomUUID().toString();
        Long result = redis.execute(CREATE, List.of(PREFIX + digest), sessionId, Long.toString(userId),
                Long.toString(now), Long.toString(end), Long.toString(authVersion), safe(clientType, 32),
                safe(ip, 64), safe(userAgent, 200), Long.toString(ttl));
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Session creation failed");
        }
        return new SessionInfo(sessionId, userId, now, end, authVersion, false, ttl);
    }

    @Override
    public SessionInfo authenticateAndRenew(String digest, boolean renewAllowed) {
        // 一次脚本调用完成读取与续期，返回字段顺序与 session-authenticate.lua 保持一致。
        List<?> result = redis.execute(AUTHENTICATE, List.of(PREFIX + digest),
                Long.toString(clock.instant().getEpochSecond()),
                Long.toString(properties.getSession().getIdleTimeout().toSeconds()), renewAllowed ? "1" : "0");
        if (result == null || result.isEmpty()) {
            return null;
        }
        String status = result.get(0).toString();
        if (!"OK".equals(status)) {
            if ("EXPIRED".equals(status)) {
                telemetry.audit("SESSION_REVOKED", null, "absolute_expiry");
            }
            return null;
        }
        return new SessionInfo(result.get(1).toString(), number(result, 2), number(result, 3),
                number(result, 4), number(result, 5), number(result, 6) == 1, number(result, 7));
    }

    @Override
    public void revokeByDigest(String digest) {
        redis.delete(PREFIX + digest);
    }

    private long number(List<?> values, int i) {
        return Long.parseLong(values.get(i).toString());
    }

    private String safe(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(max, value.length()));
    }
}
