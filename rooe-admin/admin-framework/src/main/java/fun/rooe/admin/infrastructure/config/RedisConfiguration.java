/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package fun.rooe.admin.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Zheng Jie
 * @date 2025-01-13
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class RedisConfiguration implements CachingConfigurer {

    // 反序列化白名单：仅放行项目自身与 JDK 基础类型，避免任意类型实例化。
    private static final String[] AUTO_TYPE_ALLOWLIST = {
            "fun.rooe.", "java.lang.", "java.util.", "java.sql.", "java.math.", "java.time."
    };

    // 类型信息属性名，与 Jackson 默认约定保持一致
    private static final String TYPE_PROPERTY = "@class";

    /**
     *  设置 redis 数据默认过期时间，默认2小时
     *  设置@cacheable 序列化方式
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(ObjectMapper objectMapper) {
        RedisSerializer<Object> valueSerializer = new JacksonRedisSerializer(objectMapper);
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig();
        configuration = configuration.serializeValuesWith(RedisSerializationContext.
                SerializationPair.fromSerializer(valueSerializer)).entryTtl(Duration.ofHours(2));
        return configuration;
    }

    @Bean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 指定 key 和 value 的序列化方案
        RedisSerializer<Object> valueSerializer = new JacksonRedisSerializer(objectMapper);
        // value值的序列化采用JacksonRedisSerializer
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        // key的序列化采用StringRedisSerializer
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    /**
     * 缓存管理器
     * @param redisConnectionFactory /
     * @return 缓存管理器
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                          RedisCacheConfiguration config) {
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * 自定义缓存key生成策略
     */
    @Bean
    public KeyGenerator keyGenerator(ObjectMapper objectMapper) {
        return (target, method, params) -> {
            Map<String,Object> container = new HashMap<>(8);
            Class<?> targetClassClass = target.getClass();
            // 类地址
            container.put("class",targetClassClass.toGenericString());
            // 方法名称
            container.put("methodName",method.getName());
            // 包名称
            container.put("package",targetClassClass.getPackage());
            // 参数列表
            for (int i = 0; i < params.length; i++) {
                container.put(String.valueOf(i),params[i]);
            }
            // 转为JSON字符串
            String jsonString;
            try {
                jsonString = objectMapper.writeValueAsString(container);
            } catch (JsonProcessingException exception) {
                log.warn("Cache key parameters could not be serialized as JSON, fallback to toString: {}",
                        exception.getMessage());
                jsonString = container.toString();
            }
            // 使用 MurmurHash 生成 hash
            return Integer.toHexString(MurmurHash3.hash32x86(jsonString.getBytes(StandardCharsets.UTF_8)));
        };
    }

    @Bean
    @SuppressWarnings({"unchecked","all"})
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                // 处理缓存读取错误
                log.error("Cache Get Error: {}",exception.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                // 处理缓存写入错误
                log.error("Cache Put Error: {}",exception.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // 处理缓存删除错误
                log.error("Cache Evict Error: {}",exception.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                // 处理缓存清除错误
                log.error("Cache Clear Error: {}",exception.getMessage());
            }
        };
    }

    /**
     * Value 序列化（Jackson 实现）
     *
     * 写入类型信息以支持多态反序列化，反序列化时仅接受白名单内的类型；
     * 无法识别的历史数据按缓存未命中处理，由调用方回源查询。
     *
     * @author /
     */
    static class JacksonRedisSerializer implements RedisSerializer<Object> {

        private final ObjectMapper objectMapper;

        JacksonRedisSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper.copy();
            BasicPolymorphicTypeValidator.Builder validatorBuilder = BasicPolymorphicTypeValidator.builder();
            for (String packagePrefix : AUTO_TYPE_ALLOWLIST) {
                validatorBuilder.allowIfSubType(packagePrefix);
            }
            PolymorphicTypeValidator typeValidator = validatorBuilder.build();
            this.objectMapper.activateDefaultTypingAsProperty(
                    typeValidator, ObjectMapper.DefaultTyping.EVERYTHING, TYPE_PROPERTY);
        }

        @Override
        public byte[] serialize(Object value) throws SerializationException
        {
            if (value == null) {
                return new byte[0];
            }
            try {
                return objectMapper.writeValueAsBytes(value);
            } catch (JsonProcessingException exception) {
                throw new SerializationException("Could not serialize Redis value", exception);
            }
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException
        {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            try {
                return objectMapper.readValue(bytes, Object.class);
            } catch (Exception exception) {
                // 历史数据可能由旧序列化器写入，按缓存未命中处理，避免升级期间接口报错
                log.warn("Could not deserialize Redis value, treating it as a cache miss: {}",
                        exception.getMessage());
                return null;
            }
        }

    }
}
