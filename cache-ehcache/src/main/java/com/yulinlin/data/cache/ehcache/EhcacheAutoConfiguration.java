package com.yulinlin.data.cache.ehcache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "com.yulinlin.data.core.YulinlinCoreAutoConfig")
@EnableConfigurationProperties({QueryCacheProperties.class, EhcacheProperties.class})
public class EhcacheAutoConfiguration {

    @Bean(destroyMethod = "close")
    public EhcacheQueryCache ehcacheQueryCache(QueryCacheProperties common,
                                               EhcacheProperties ehcache,
                                               ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new EhcacheQueryCache(common, ehcache,
                objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }
}
