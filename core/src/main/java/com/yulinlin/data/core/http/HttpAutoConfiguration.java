package com.yulinlin.data.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** HTTP 执行器、客户端和静态入口的统一装配；不依赖 ORM Session 的初始化。 */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, RestClientAutoConfiguration.class})
@EnableConfigurationProperties(HttpRequestProperties.class)
public class HttpAutoConfiguration {

    // 完整自定义客户端时，两项默认装配一起退让；不依赖 @Bean 方法读取顺序。
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(HttpRequestClient.class)
    static class DefaultClientConfiguration {
        @Bean
        @ConditionalOnMissingBean(HttpExecutor.class)
        public HttpExecutor httpExecutor(ObjectProvider<RestClient.Builder> builders,
                                         ObjectProvider<ObjectMapper> mappers, HttpRequestProperties properties) {
            return RestClientHttpExecutor.create(builders.getIfAvailable(RestClient::builder),
                    mappers.getIfAvailable(ObjectMapper::new), properties);
        }

        @Bean
        public HttpRequestClient httpRequestClient(ObjectProvider<HttpExecutor> executors,
                                                   ObjectProvider<ObjectMapper> mappers, HttpRequestProperties properties) {
            HttpExecutor executor;
            try {
                executor = executors.getObject();
            } catch (NoUniqueBeanDefinitionException error) {
                throw new IllegalStateException("存在多个 HttpExecutor，请通过 @Primary 明确选择默认 HTTP 执行器", error);
            }
            // 共享执行器 Bean 由 Spring 单独关闭，客户端不重复关闭它。
            return new HttpRequestClient(executor, mappers.getIfAvailable(ObjectMapper::new), properties);
        }
    }

    @Bean
    @ConditionalOnMissingBean(HttpClientBinding.class)
    public HttpClientBinding httpClientBinding(ObjectProvider<HttpRequestClient> clients) {
        return new HttpClientBinding(clients);
    }
}
