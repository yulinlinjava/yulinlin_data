package com.yulinlin.regression.security;

import com.yulinlin.security.SecurityAutoConfiguration;
import com.yulinlin.security.SecurityCryptoProperties;
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoContext;
import com.yulinlin.security.crypto.DeviceCryptoManager;
import com.yulinlin.security.limit.ApiLimitInterceptor;
import com.yulinlin.security.limit.ApiLimitWebMvcConfigurer;
import com.yulinlin.security.limit.IpBlacklist;
import com.yulinlin.security.web.CryptoPathExclusions;
import com.yulinlin.security.web.CryptoRequestBodyAdvice;
import com.yulinlin.security.web.CryptoResponseBodyAdvice;
import com.yulinlin.security.web.DeviceTypeResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;

/** 只启动最小 Web 容器验证 Bean 条件，不启动业务应用或连接外部服务。 */
class CryptoAdviceConfigurationTest {

    private final CryptoCodec customCodec = new CryptoCodec() {
        @Override
        public String encrypt(CryptoContext context, String plaintext) {
            return "test:" + plaintext;
        }

        @Override
        public String decrypt(CryptoContext context, String ciphertext) {
            return ciphertext.substring("test:".length());
        }
    };

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                JacksonAutoConfiguration.class, SecurityAutoConfiguration.class));
    }

    private WebApplicationContextRunner customCodecRunner() {
        return runner().withBean(CryptoCodec.class, () -> customCodec);
    }

    @Test
    void adviceIsEnabledByDefault() {
        assertThat(new SecurityCryptoProperties().isAdviceEnabled()).isTrue();
        customCodecRunner().run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(CryptoRequestBodyAdvice.class)
                    .hasSingleBean(CryptoResponseBodyAdvice.class);
            assertThat(context.getBean(SecurityCryptoProperties.class).isAdviceEnabled()).isTrue();
            assertThat(context.getBean(CryptoCodec.class)).isSameAs(customCodec);
        });
    }

    @Test
    void adviceCanBeExplicitlyEnabled() {
        customCodecRunner().withPropertyValues("yulinlin.security.crypto.advice-enabled=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(CryptoRequestBodyAdvice.class)
                        .hasSingleBean(CryptoResponseBodyAdvice.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void disablingAdviceKeepsCustomCodecAndOtherSecurityBeans(boolean enabled) {
        customCodecRunner().withPropertyValues(
                "yulinlin.security.crypto.advice-enabled=false",
                "yulinlin.security.crypto.enabled=" + enabled).run(context -> {
            assertThat(context).hasNotFailed()
                    .doesNotHaveBean(CryptoRequestBodyAdvice.class)
                    .doesNotHaveBean(CryptoResponseBodyAdvice.class)
                    .hasSingleBean(CryptoCodec.class)
                    .hasSingleBean(DeviceTypeResolver.class)
                    .hasSingleBean(CryptoPathExclusions.class)
                    .hasSingleBean(IpBlacklist.class)
                    .hasSingleBean(ApiLimitInterceptor.class)
                    .hasSingleBean(ApiLimitWebMvcConfigurer.class);
            assertThat(context.getBean(SecurityCryptoProperties.class).isAdviceEnabled()).isFalse();
            CryptoCodec codec = context.getBean(CryptoCodec.class);
            assertThat(codec).isSameAs(customCodec);
            CryptoContext cryptoContext = CryptoContext.of(null);
            String ciphertext = codec.encrypt(cryptoContext, "手动调用");
            assertThat(codec.decrypt(cryptoContext, ciphertext)).isEqualTo("手动调用");
        });
    }

    @Test
    void disablingAdviceKeepsDefaultAesCodecUsable() {
        runner().withPropertyValues(
                "yulinlin.security.crypto.advice-enabled=false",
                "yulinlin.security.crypto.devices.unknown.key=0123456789abcdef",
                "yulinlin.security.crypto.devices.unknown.iv=abcdef0123456789").run(context -> {
            assertThat(context).hasNotFailed()
                    .doesNotHaveBean(CryptoRequestBodyAdvice.class)
                    .doesNotHaveBean(CryptoResponseBodyAdvice.class)
                    .hasSingleBean(DeviceCryptoManager.class)
                    .hasSingleBean(CryptoCodec.class);
            CryptoCodec codec = context.getBean(CryptoCodec.class);
            CryptoContext cryptoContext = CryptoContext.of(null);
            String ciphertext = codec.encrypt(cryptoContext, "AES 手动调用");
            assertThat(ciphertext).isNotEqualTo("AES 手动调用");
            assertThat(codec.decrypt(cryptoContext, ciphertext)).isEqualTo("AES 手动调用");
        });
    }

    @Test
    void existingCompatibilitySwitchDoesNotRemoveAdvice() {
        customCodecRunner().withPropertyValues("yulinlin.security.crypto.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(CryptoRequestBodyAdvice.class)
                        .hasSingleBean(CryptoResponseBodyAdvice.class));
    }

    @Test
    void adviceStillRequiresCodec() {
        runner().run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(CryptoCodec.class)
                .doesNotHaveBean(CryptoRequestBodyAdvice.class)
                .doesNotHaveBean(CryptoResponseBodyAdvice.class));
    }

    @Test
    void componentScanningCannotReEnableDisabledAdvice() {
        customCodecRunner().withUserConfiguration(AdviceScanConfiguration.class)
                .withPropertyValues("yulinlin.security.crypto.advice-enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(CryptoCodec.class)
                        .doesNotHaveBean(CryptoRequestBodyAdvice.class)
                        .doesNotHaveBean(CryptoResponseBodyAdvice.class));
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = CryptoRequestBodyAdvice.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {CryptoRequestBodyAdvice.class, CryptoResponseBodyAdvice.class}))
    static class AdviceScanConfiguration {
    }
}
