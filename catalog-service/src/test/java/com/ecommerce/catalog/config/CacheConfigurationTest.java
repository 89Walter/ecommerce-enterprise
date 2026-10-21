package com.ecommerce.catalog.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CacheConfigurationTest {

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void productCacheIsTransactionAware() {
        Cache cache = productCache();

        assertThat(cache).isInstanceOf(TransactionAwareCacheDecorator.class);
    }

    @Test
    void putInsideTransactionWaitsForCommitAndIsDroppedOnRollback() {
        Cache redisCache = mock(Cache.class);
        Cache cache = new TransactionAwareCacheDecorator(redisCache);
        TransactionSynchronizationManager.initSynchronization();

        cache.put(7L, "valore nuovo");

        verify(redisCache, never()).put(7L, "valore nuovo");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(redisCache, never()).put(7L, "valore nuovo");
    }

    private static Cache productCache() {
        RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.builder(mock(RedisConnectionFactory.class));
        new CacheConfiguration().productCacheCustomizer(Duration.ofMinutes(10)).customize(builder);
        RedisCacheManager cacheManager = builder.build();
        cacheManager.afterPropertiesSet();
        return cacheManager.getCache(CacheConfiguration.PRODUCTS_CACHE);
    }
}
