package fun.rooe.admin.infrastructure.security;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

final class RedisScripts {
    private RedisScripts() {
    }

    static <T> DefaultRedisScript<T> load(String resource, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(resource));
        script.setResultType(type);
        return script;
    }
}
