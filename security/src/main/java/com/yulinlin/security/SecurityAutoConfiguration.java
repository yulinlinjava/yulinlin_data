package com.yulinlin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoConfiguredCondition;
import com.yulinlin.security.limit.ApiLimitInterceptor;
import com.yulinlin.security.limit.ApiLimitWebMvcConfigurer;
import com.yulinlin.security.limit.IpBlacklist;
import com.yulinlin.security.web.CryptoRequestBodyAdvice;
import com.yulinlin.security.web.CryptoResponseBodyAdvice;
import com.yulinlin.security.web.CryptoPathExclusions;
import com.yulinlin.security.web.DeviceTypeResolver;
import com.yulinlin.security.web.HeaderDeviceTypeResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

@AutoConfiguration(after = {JacksonAutoConfiguration.class, WebMvcAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestBodyAdvice.class)
@EnableConfigurationProperties({SecurityCryptoProperties.class, SecurityLimitProperties.class})
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CryptoCodec.class)
    @Conditional(CryptoConfiguredCondition.class)
    public DeviceCryptoManager deviceCryptoManager(SecurityCryptoProperties properties) {
        return new DeviceCryptoManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CryptoCodec.class)
    public DeviceTypeResolver deviceTypeResolver(SecurityCryptoProperties properties) {
        return new HeaderDeviceTypeResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CryptoCodec.class)
    public CryptoPathExclusions cryptoPathExclusions(SecurityCryptoProperties properties) {
        return new CryptoPathExclusions(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CryptoCodec.class)
    @ConditionalOnProperty(prefix = "yulinlin.security.crypto", name = "advice-enabled",
            havingValue = "true", matchIfMissing = true)
    public CryptoRequestBodyAdvice cryptoRequestBodyAdvice(
            CryptoCodec cryptoCodec,
            DeviceTypeResolver deviceTypeResolver,
            SecurityCryptoProperties properties,
            CryptoPathExclusions pathExclusions) {
        return new CryptoRequestBodyAdvice(cryptoCodec, deviceTypeResolver, properties, pathExclusions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CryptoCodec.class)
    @ConditionalOnProperty(prefix = "yulinlin.security.crypto", name = "advice-enabled",
            havingValue = "true", matchIfMissing = true)
    public CryptoResponseBodyAdvice cryptoResponseBodyAdvice(
            CryptoCodec cryptoCodec,
            DeviceTypeResolver deviceTypeResolver,
            ObjectMapper objectMapper,
            SecurityCryptoProperties properties,
            CryptoPathExclusions pathExclusions) {
        return new CryptoResponseBodyAdvice(
                cryptoCodec, deviceTypeResolver, objectMapper, properties, pathExclusions);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "yulinlin.security.limit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public IpBlacklist ipBlacklist() {
        return ip -> false;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "yulinlin.security.limit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ApiLimitInterceptor apiLimitInterceptor(
            SecurityLimitProperties properties, IpBlacklist ipBlacklist) {
        return new ApiLimitInterceptor(properties, ipBlacklist);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "yulinlin.security.limit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ApiLimitWebMvcConfigurer apiLimitWebMvcConfigurer(ApiLimitInterceptor interceptor) {
        return new ApiLimitWebMvcConfigurer(interceptor);
    }
}
