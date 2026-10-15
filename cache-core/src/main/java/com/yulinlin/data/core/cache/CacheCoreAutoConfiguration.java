package com.yulinlin.data.core.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Exposes the public cache API independently from the ORM core. */
@AutoConfiguration(afterName = {
        "com.yulinlin.data.cache.caffeine.CaffeineCacheAutoConfiguration",
        "com.yulinlin.data.cache.ehcache.EhcacheAutoConfiguration",
        "com.yulinlin.data.cache.redis.RedisCacheAutoConfiguration"
})
public class CacheCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheClient cacheClient(ObjectProvider<QueryCache> queryCaches) {
        return new DefaultCacheClient(QueryCaches.single(queryCaches.orderedStream().toList()));
    }
}
