package com.yulinlin.security.jwt;

import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.session.LoginSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NativeConfigurationTest {
    private final JwtTokenCodec custom = new JwtTokenCodec() {
        @Override public String generate(IUserDetails user, String deviceType, String authType) {
            throw new UnsupportedOperationException("测试不签发凭证");
        }
        @Override public TokenInfo parse(String token) { return null; }
    };
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtAutoConfiguration.class, SecurityAuthAutoConfiguration.class))
            .withBean(UserStore.class, () -> id -> null)
            .withBean(JwtTokenCodec.class, () -> custom);

    @Test void customCodecNeedsNoDefaultSecretAndDoesNotEnableSessions() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtSecurityManager.class)
                    .doesNotHaveBean(DefaultJwtTokenCodec.class).doesNotHaveBean(LoginSessionManager.class);
            assertThat(context.getBean(JwtTokenCodec.class)).isSameAs(custom);
        });
    }

    @Test void explicitSessionOptInFails() {
        runner.withPropertyValues("yulinlin.security.session.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("不支持 yulinlin.security.session.enabled=true"));
    }

    @Test void explicitOptOutIsAllowed() {
        runner.withPropertyValues("yulinlin.security.session.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(LoginSessionManager.class));
    }

    @Test void customManagerBacksOffAdapterWithoutDemandingSecret() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JwtAutoConfiguration.class, SecurityAuthAutoConfiguration.class))
                .withBean(ISecurityManager.class, AnonymousSecurityManager::new)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JwtSecurityManager.class)
                        .doesNotHaveBean(JwtTokenCodec.class));
    }
}
