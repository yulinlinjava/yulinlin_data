package com.yulinlin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.crypto.AesCrypto;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import com.yulinlin.security.web.CryptoRequestBodyAdvice;
import com.yulinlin.security.web.CryptoResponseBodyAdvice;
import com.yulinlin.security.web.DeviceTypeResolver;
import com.yulinlin.security.web.HeaderDeviceTypeResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

@AutoConfiguration(after = {JacksonAutoConfiguration.class, WebMvcAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestBodyAdvice.class)
@EnableConfigurationProperties(SecurityCryptoProperties.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeviceCryptoManager deviceCryptoManager(SecurityCryptoProperties properties) {
        return new DeviceCryptoManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceTypeResolver deviceTypeResolver(SecurityCryptoProperties properties) {
        return new HeaderDeviceTypeResolver(properties);
    }

    /** Exposes the configured default device cipher for simple single-device use. */
    @Bean
    @ConditionalOnMissingBean
    public AesCrypto aesCrypto(DeviceCryptoManager cryptoManager) {
        return cryptoManager.getDefaultCrypto();
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoRequestBodyAdvice cryptoRequestBodyAdvice(
            DeviceCryptoManager cryptoManager,
            DeviceTypeResolver deviceTypeResolver,
            SecurityCryptoProperties properties) {
        return new CryptoRequestBodyAdvice(cryptoManager, deviceTypeResolver, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoResponseBodyAdvice cryptoResponseBodyAdvice(
            DeviceCryptoManager cryptoManager,
            DeviceTypeResolver deviceTypeResolver,
            ObjectMapper objectMapper,
            SecurityCryptoProperties properties) {
        return new CryptoResponseBodyAdvice(cryptoManager, deviceTypeResolver, objectMapper, properties);
    }
}
