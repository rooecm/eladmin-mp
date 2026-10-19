package fun.rooe.admin.infrastructure.security;

import fun.rooe.admin.framework.security.AuthTelemetry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 缓存读写设施；业务服务决定回源策略，此处负责过期时间与原子写入。
 */
@Component
@RequiredArgsConstructor
public class RedisAuthCache {
    public static final String USER = "rooe:auth:user:";
    public static final String ROLE = "rooe:auth:role-permissions:";
    private static final DefaultRedisScript<Long> FILL = RedisScripts.load("security/cache-fill.lua", Long.class);
    private static final DefaultRedisScript<Long> REFRESH = RedisScripts.load("security/cache-refresh.lua", Long.class);
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final AuthTelemetry telemetry;

    public long deadline(Duration ttl) {
        // TTL 上下浮动 10%，分散同一批用户与角色缓存的过期时间。
        return clock.millis() + (long) (ttl.toMillis() * ThreadLocalRandom.current().nextDouble(0.9, 1.1));
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public List<String> multiGet(List<String> keys) {
        return redis.opsForValue().multiGet(keys);
    }

    /**
     * 仅填充空键，使用查询前计算的绝对过期时间，不覆盖已存在的缓存。
     */
    public void fill(String key, String json, long deadline) {
        if (deadline > clock.millis()) {
            redis.execute(FILL, List.of(key), json, Long.toString(deadline));
        }
    }

    /**
     * 仅当键不存在或仍是 observed 时刷新，避免覆盖期间被其他请求替换的值。
     */
    public void refresh(String key, String observed, String json, long deadline) {
        if (deadline > clock.millis()) {
            redis.execute(REFRESH, List.of(key), observed, json, Long.toString(deadline));
        }
    }

    public void delete(Collection<String> keys) {
        if (!keys.isEmpty()) redis.delete(keys);
    }

    public void failure() {
        telemetry.count("auth_redis_error_total", "op", "cache");
    }
}
