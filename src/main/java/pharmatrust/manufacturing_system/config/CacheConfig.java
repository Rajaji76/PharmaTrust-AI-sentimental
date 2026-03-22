package pharmatrust.manufacturing_system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache configuration with Redis primary and in-memory fallback.
 * If Redis is unavailable (e.g. dev environment), falls back to ConcurrentMapCacheManager.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${cache.redis.enabled:true}")
    private boolean redisEnabled;

    @Bean
    @Primary
    public CacheManager cacheManager() {
        if (redisEnabled) {
            try {
                RedisConnectionFactory factory = redisConnectionFactory();
                // Test connection
                factory.getConnection().ping();

                RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer()))
                        .disableCachingNullValues();

                log.info("Redis cache connected — host: {}:{}", redisHost, redisPort);

                return RedisCacheManager.builder(factory)
                        .cacheDefaults(config)
                        .withCacheConfiguration("manufacturer-keys",
                                config.entryTtl(Duration.ofHours(1)))
                        .withCacheConfiguration("batch-data",
                                config.entryTtl(Duration.ofMinutes(15)))
                        .withCacheConfiguration("public-keys",
                                config.entryTtl(Duration.ofHours(24)))
                        .build();

            } catch (Exception e) {
                log.warn("Redis unavailable ({}), falling back to in-memory cache", e.getMessage());
            }
        }

        log.info("Using in-memory cache (ConcurrentMapCacheManager)");
        return new ConcurrentMapCacheManager("manufacturer-keys", "batch-data", "public-keys");
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isBlank()) {
            config.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(config);
    }
}
