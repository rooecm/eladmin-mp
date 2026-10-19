package fun.rooe.admin.infrastructure.security;

import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.framework.security.model.SessionInfo;
import fun.rooe.admin.framework.security.model.SessionTokens;
import fun.rooe.admin.framework.security.service.RefreshReplayException;
import fun.rooe.admin.framework.security.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Redis 会话实现：Access 与 Refresh 双凭证按摘要保存，创建、校验、轮换与吊销均由 Lua 保证原子性。
 * 会话数据集中在 session-id 索引中，轮换只替换凭证摘要，不复制会话内容。
 */
@Service
@RequiredArgsConstructor
public class RedisSessionService implements SessionService {
    public static final String ACCESS_PREFIX = "rooe:auth:session:";
    public static final String REFRESH_PREFIX = "rooe:auth:refresh:";
    public static final String SESSION_PREFIX = "rooe:auth:sid:";
    public static final String REPLAY_PREFIX = "rooe:auth:replay:";
    private static final DefaultRedisScript<Long> CREATE = RedisScripts.load("security/session-create.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> AUTHENTICATE = RedisScripts.load("security/session-authenticate.lua", List.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> REFRESH = RedisScripts.load("security/session-refresh.lua", List.class);
    private static final DefaultRedisScript<Long> REVOKE = RedisScripts.load("security/session-revoke.lua", Long.class);
    private final StringRedisTemplate redis;
    private final AuthProperties properties;
    private final Clock clock;
    private final AuthTelemetry telemetry;

    @Override
    public SessionInfo create(String accessDigest, String refreshDigest, long userId, long authVersion,
                              String clientType, String ip, String userAgent) {
        long now = clock.instant().getEpochSecond();
        long end = now + properties.getSession().getRefreshTtl().toSeconds();
        String sessionId = UUID.randomUUID().toString();
        // StringRedisSerializer 要求键与参数全部为字符串，数值必须先显式转换。
        Long result = redis.execute(CREATE, List.of(ACCESS_PREFIX + accessDigest, REFRESH_PREFIX + refreshDigest,
                        SESSION_PREFIX + sessionId), Long.toString(properties.getSession().getAccessTtl().toSeconds()),
                Long.toString(properties.getSession().getRefreshTtl().toSeconds()), sessionId, Long.toString(userId),
                Long.toString(now), Long.toString(authVersion), safe(clientType, 32), safe(ip, 64), safe(userAgent, 200),
                Long.toString(end), accessDigest, refreshDigest);
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Session creation failed");
        }
        return new SessionInfo(sessionId, userId, now, end, authVersion, false,
                properties.getSession().getAccessTtl().toSeconds());
    }

    @Override
    public SessionInfo authenticate(String accessDigest) {
        List<?> result = redis.execute(AUTHENTICATE, List.of(ACCESS_PREFIX + accessDigest),
                Long.toString(clock.instant().getEpochSecond()));
        if (result == null || result.isEmpty() || !"OK".equals(result.get(0).toString())) {
            if (result != null && !result.isEmpty() && "EXPIRED".equals(result.get(0).toString())) {
                telemetry.audit("SESSION_REVOKED", null, "absolute_expiry");
            }
            return null;
        }
        return new SessionInfo(result.get(1).toString(), number(result, 2), number(result, 3),
                number(result, 4), number(result, 5), false, number(result, 6));
    }

    @Override
    public SessionTokens refresh(String oldRefreshDigest, String newAccessDigest, String newRefreshDigest) {
        String newSessionId = UUID.randomUUID().toString();
        List<?> result = redis.execute(REFRESH, List.of(
                        REFRESH_PREFIX + oldRefreshDigest, ACCESS_PREFIX + newAccessDigest,
                        REFRESH_PREFIX + newRefreshDigest, REPLAY_PREFIX + oldRefreshDigest,
                        SESSION_PREFIX + newSessionId),
                newAccessDigest, newRefreshDigest, newSessionId, Long.toString(clock.instant().getEpochSecond()),
                Long.toString(properties.getSession().getAccessTtl().toSeconds()),
                Long.toString(properties.getSession().getRefreshTtl().toSeconds()),
                Long.toString(properties.getSession().getReplayMarkerTtl().toSeconds()));
        if (result == null || result.isEmpty()) {
            return null;
        }
        String status = result.get(0).toString();
        if ("REPLAY".equals(status)) {
            telemetry.count("auth_refresh_replay_total");
            telemetry.audit("REFRESH_REPLAY", null, "refresh_reused");
            throw new RefreshReplayException("Refresh token replayed");
        }
        if (!"OK".equals(status)) {
            return null;
        }
        return new SessionTokens(result.get(1).toString(), number(result, 2), number(result, 3),
                number(result, 4), number(result, 5), number(result, 6));
    }

    @Override
    public void revokeBySessionId(String sessionId) {
        if (sessionId != null) {
            redis.execute(REVOKE, List.of(SESSION_PREFIX + sessionId));
        }
    }

    @Override
    public void revokeByAccessDigest(String accessDigest) {
        if (accessDigest == null) {
            return;
        }
        // 凭证键只保存会话标识，凭它索引到会话即可一次性吊销 Access 与 Refresh。
        String sessionId = redis.opsForValue().get(ACCESS_PREFIX + accessDigest);
        if (sessionId != null) {
            redis.execute(REVOKE, List.of(SESSION_PREFIX + sessionId));
        } else {
            redis.delete(ACCESS_PREFIX + accessDigest);
        }
    }

    private long number(List<?> values, int i) {
        return Long.parseLong(values.get(i).toString());
    }

    private String safe(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(max, value.length()));
    }
}
