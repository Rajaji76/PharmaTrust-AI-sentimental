package pharmatrust.manufacturing_system.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple in-memory cache configuration (no Redis required in dev).
 * For production, swap ConcurrentMapCacheManager with RedisCacheManager.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "manufacturer-keys",
            "batch-data",
            "public-keys"
        );
    }
}
