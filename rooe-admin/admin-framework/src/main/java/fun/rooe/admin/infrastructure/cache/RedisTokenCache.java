package fun.rooe.admin.infrastructure.cache;

import fun.rooe.admin.framework.cache.TokenCache;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed token cache adapter.
 */
@Component
public class RedisTokenCache implements TokenCache {

    private final RedisUtils redisUtils;

    public RedisTokenCache(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    @Override
    public long getExpire(String key) {
        return redisUtils.getExpire(key);
    }

    @Override
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return redisUtils.expire(key, timeout, unit);
    }
}
