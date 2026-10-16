package fun.rooe.admin.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RedisConfigurationTest {

    private final RedisConfiguration configuration = new RedisConfiguration();

    @Test
    @SuppressWarnings("unchecked")
    void configuresStringKeysAndCompatibleJsonValues() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = configuration.redisTemplate(connectionFactory);

        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer());
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer());

        CacheValue expected = new CacheValue("value");
        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) template.getValueSerializer();
        byte[] serialized = valueSerializer.serialize(expected);
        assertNotNull(serialized);
        Object deserialized = valueSerializer.deserialize(serialized);
        assertEquals(expected, deserialized);
    }

    @Test
    void usesTwoHourDefaultCacheTtl() {
        RedisCacheConfiguration cacheConfiguration = configuration.redisCacheConfiguration();

        assertEquals(Duration.ofHours(2), cacheConfiguration.getTtlFunction().getTimeToLive("cache", null));
    }

    public static class CacheValue {

        private String value;

        public CacheValue() {
        }

        CacheValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof CacheValue that && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
