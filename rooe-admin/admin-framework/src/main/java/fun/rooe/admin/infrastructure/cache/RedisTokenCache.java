package fun.rooe.admin.infrastructure.cache;

import fun.rooe.admin.framework.cache.TokenCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed token cache adapter.
 */
@Component
public class RedisTokenCache implements TokenCache {

    private final StringRedisTemplate redisTemplate;

    public RedisTokenCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    @Override
    public boolean expire(String key, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, timeout, unit);
        return Boolean.TRUE.equals(result);
    }
}
