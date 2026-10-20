package com.yulinlin.data.core.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Exposes the public cache API independently from the ORM core. */
@AutoConfiguration(beforeName = "com.yulinlin.data.core.YulinlinCoreAutoConfig", afterName = {
        "com.yulinlin.data.cache.caffeine.CaffeineCacheAutoConfiguration",
        "com.yulinlin.data.cache.ehcache.EhcacheAutoConfiguration",
        "com.yulinlin.data.cache.redis.RedisCacheAutoConfiguration"
})
@EnableConfigurationProperties(QueryCacheProperties.class)
public class CacheCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheRegistry cacheRegistry(ObjectProvider<QueryCache> queryCaches, QueryCacheProperties properties) {
        return new CacheRegistry(queryCaches.orderedStream().toList(), properties.getDefault());
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheClient cacheClient(CacheRegistry registry) {
        return new RoutingCacheClient(registry);
    }
}
