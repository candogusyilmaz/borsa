package dev.canverse.stocks.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfiguration {
    @Bean
    public CaffeineCacheManager cacheManager() {
        var cacheManager = new CaffeineCacheManager();

        registerUserCache(cacheManager);

        return cacheManager;
    }

    private static void registerUserCache(CaffeineCacheManager cacheManager) {
        var config = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build();

        cacheManager.registerCustomCache("userCacheByUsername", config);
        cacheManager.registerCustomCache("userCacheByEmail", config);
    }
}
