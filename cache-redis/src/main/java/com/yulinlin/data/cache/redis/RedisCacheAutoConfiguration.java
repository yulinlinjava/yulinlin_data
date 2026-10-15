package com.yulinlin.data.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.cache.QueryCacheProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@AutoConfiguration(after = RedisAutoConfiguration.class,
        beforeName = "com.yulinlin.data.core.YulinlinCoreAutoConfig")
@EnableConfigurationProperties({QueryCacheProperties.class, RedisCacheProperties.class})
public class RedisCacheAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisQueryCache redisQueryCache(QueryCacheProperties common,
                                           RedisCacheProperties redis,
                                           RedisConnectionFactory connectionFactory,
                                           ObjectProvider<ObjectMapper> objectMapper) {
        return new RedisQueryCache(common, redis, connectionFactory,
                objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
