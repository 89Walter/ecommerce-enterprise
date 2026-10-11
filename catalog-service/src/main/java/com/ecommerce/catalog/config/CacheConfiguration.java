package com.ecommerce.catalog.config;

import com.ecommerce.catalog.product.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Cache Redis per la lettura dei singoli prodotti.
 * <p>
 * Ogni cache ha un serializzatore JSON tipizzato (niente informazioni di classe nei valori salvati).
 * Se Redis non è raggiungibile, gli errori vengono registrati nei log e la richiesta prosegue sul database:
 * la cache migliora le prestazioni ma non è mai un punto di guasto.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfiguration implements CachingConfigurer {

    public static final String PRODUCTS_CACHE = "products";

    @Bean
    RedisCacheManagerBuilderCustomizer productCacheCustomizer(
            @Value("${catalog.cache.product-ttl}") Duration productTtl
    ) {
        return builder -> builder.withCacheConfiguration(
                PRODUCTS_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .prefixCacheNameWith("catalog::")
                        .entryTtl(productTtl)
                        .disableCachingNullValues()
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(ProductResponse.class)
                        ))
        );
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Lettura dalla cache {} non riuscita per la chiave {}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Scrittura nella cache {} non riuscita per la chiave {}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Invalidazione della cache {} non riuscita per la chiave {}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Svuotamento della cache {} non riuscito: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
