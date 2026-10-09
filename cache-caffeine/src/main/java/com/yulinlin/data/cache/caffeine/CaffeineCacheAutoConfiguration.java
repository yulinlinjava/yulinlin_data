package com.yulinlin.data.cache.caffeine;

import com.yulinlin.data.core.YulinlinCoreAutoConfig;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = YulinlinCoreAutoConfig.class)
@EnableConfigurationProperties({QueryCacheProperties.class, CaffeineCacheProperties.class})
public class CaffeineCacheAutoConfiguration {

    @Bean
    public QueryCache caffeineQueryCache(QueryCacheProperties common,
                                          CaffeineCacheProperties caffeine) {
        return new CaffeineQueryCache(common, caffeine);
    }
}
