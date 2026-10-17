package fun.rooe.admin.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RedissonSpringDataIntegrationTest {

    @Test
    void usesRedissonAsTheOnlySpringDataRedisConnectionFactory() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RedissonClient.class, () -> mock(RedissonClient.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(RedissonAutoConfigurationV2.class, RedisConfiguration.class);
            context.refresh();

            Map<String, RedisConnectionFactory> connectionFactories =
                    context.getBeansOfType(RedisConnectionFactory.class);
            assertEquals(1, connectionFactories.size());

            RedisConnectionFactory connectionFactory = connectionFactories.values().iterator().next();
            assertInstanceOf(RedissonConnectionFactory.class, connectionFactory);

            RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
            StringRedisTemplate stringRedisTemplate = context.getBean(StringRedisTemplate.class);
            assertSame(connectionFactory, redisTemplate.getConnectionFactory());
            assertSame(connectionFactory, stringRedisTemplate.getConnectionFactory());
        }
    }
}
