package fun.rooe.admin.infrastructure.cache;

import fun.rooe.admin.framework.cache.RateLimitStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Redis-backed rate-limit counter adapter.
 */
@Component
public class RedisRateLimitStore implements RateLimitStore {

    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local c" +
                    "\nc = redis.call('get',KEYS[1])" +
                    "\nif c and tonumber(c) > tonumber(ARGV[1]) then" +
                    "\nreturn c;" +
                    "\nend" +
                    "\nc = redis.call('incr',KEYS[1])" +
                    "\nif tonumber(c) == 1 then" +
                    "\nredis.call('expire',KEYS[1],ARGV[2])" +
                    "\nend" +
                    "\nreturn c;",
            Long.class);

    private final RedisTemplate<Object, Object> redisTemplate;

    public RedisRateLimitStore(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Long increment(String key, int maxCount, int periodSeconds) {
        return redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                maxCount,
                periodSeconds);
    }
}
