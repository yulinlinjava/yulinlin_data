package com.yulinlin.security.satoken;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.spring.SaBeanRegister;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.session.LoginSessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;

class NativeConfigurationTest {
    private final StpLogic previous = StpUtil.getStpLogic();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAuthAutoConfiguration.class, SaTokenAutoConfiguration.class))
            .withUserConfiguration(SaBeanRegister.class, NativeWiring.class);

    @AfterEach void restoreNativeGlobal() { StpUtil.setStpLogic(previous); }

    @Test void defaultLimitIsUnlimited() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LoginSessionManager.class);
            assertThat(context.getBean(SaTokenConfig.class).getMaxLoginCount()).isEqualTo(-1);
        });
    }

    @Test void nativeSettingsAreNotOverwritten() {
        runner.withPropertyValues("yulinlin.security.session.max-sessions=3",
                "sa-token.token-name=Authorization", "sa-token.token-prefix=Bearer",
                "sa-token.is-read-cookie=false", "sa-token.is-read-body=false",
                "sa-token.timeout=86400", "sa-token.active-timeout=1800", "sa-token.auto-renew=false",
                "sa-token.cookie.same-site=Strict", "sa-token.overflow-logout-mode=KICKOUT").run(context -> {
            assertThat(context).hasNotFailed();
            SaTokenConfig config = context.getBean(SaTokenConfig.class);
            assertThat(config.getMaxLoginCount()).isEqualTo(3);
            assertThat(config.getTokenName()).isEqualTo("Authorization");
            assertThat(config.getTokenPrefix()).isEqualTo("Bearer");
            assertThat(config.getIsReadCookie()).isFalse();
            assertThat(config.getIsReadBody()).isFalse();
            assertThat(config.getTimeout()).isEqualTo(86400);
            assertThat(config.getActiveTimeout()).isEqualTo(1800);
            assertThat(config.getAutoRenew()).isFalse();
            assertThat(config.getCookie().getSameSite()).isEqualTo("Strict");
            assertThat(config.getOverflowLogoutMode().name()).isEqualTo("KICKOUT");
        });
    }

    @Test void sameExplicitLimitIsAllowed() {
        runner.withPropertyValues("yulinlin.security.session.max-sessions=3", "sa-token.max-login-count=3")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test void propertyConflictFailsAtStartup() {
        runner.withPropertyValues("yulinlin.security.session.max-sessions=3", "sa-token.max-login-count=5")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("max-sessions"));
    }

    @Test void relaxedPropertySpellingCannotBypassValidation() {
        runner.withPropertyValues("yulinlin.security.session.max-sessions=3", "sa-token.maxLoginCount=5")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("max-sessions"));
    }

    @Test void javaConfigConflictIsNotOverwritten() {
        SaTokenConfig custom = new SaTokenConfig().setMaxLoginCount(5);
        runner.withBean("customConfig", SaTokenConfig.class, () -> custom, definition -> definition.setPrimary(true))
                .withPropertyValues("yulinlin.security.session.max-sessions=3")
                .run(context -> {
                    assertThat(context.getStartupFailure()).hasMessageContaining("SaTokenConfig/StpLogic");
                    assertThat(custom.getMaxLoginCount()).isEqualTo(5);
                });
    }

    @Test void explicitJavaDefaultValueIsAlsoAConflict() {
        runner.withBean("customConfig", SaTokenConfig.class, SaTokenConfig::new, definition -> definition.setPrimary(true))
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("SaTokenConfig/StpLogic"));
    }

    @Test void independentStpLogicConfigurationCannotBypassValidation() {
        SaTokenConfig independent = new SaTokenConfig().setMaxLoginCount(7);
        runner.withBean(StpLogic.class, () -> new StpLogic("login").setConfig(independent))
                .withPropertyValues("yulinlin.security.session.max-sessions=3")
                .run(context -> {
                    assertThat(context.getStartupFailure()).hasMessageContaining("SaTokenConfig/StpLogic");
                    assertThat(independent.getMaxLoginCount()).isEqualTo(7);
                });
    }

    @Test void matchingJavaConfigurationIsRetained() {
        SaTokenConfig custom = new SaTokenConfig().setMaxLoginCount(3).setTimeout(1234);
        runner.withBean("customConfig", SaTokenConfig.class, () -> custom, definition -> definition.setPrimary(true))
                .withPropertyValues("yulinlin.security.session.max-sessions=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SaTokenSecurityManager.class).stpLogic().getConfigOrGlobal()).isSameAs(custom);
                    assertThat(custom.getTimeout()).isEqualTo(1234);
                });
    }

    @Test void nativeMutuallyExclusiveLoginConflictsWithUnifiedManagement() {
        runner.withPropertyValues("sa-token.is-concurrent=false")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("is-concurrent"));
    }

    @Test void sharedTokensAreRejectedInsteadOfSilentlyOverridden() {
        runner.withPropertyValues("sa-token.is-share=true")
                .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("is-share"));
    }

    @Test void disablingManagementPreservesNativeLimitAndLoginMode() {
        runner.withPropertyValues("yulinlin.security.session.enabled=false",
                "yulinlin.security.session.max-sessions=3", "sa-token.max-login-count=5", "sa-token.is-concurrent=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(LoginSessionManager.class);
                    assertThat(context).hasSingleBean(SaTokenSecurityManager.class);
                    assertThat(context.getBean(SaTokenConfig.class).getMaxLoginCount()).isEqualTo(5);
                });
    }

    @Test void customSecurityManagerBacksOffAllAdapterPolicies() {
        runner.withBean(ISecurityManager.class, AnonymousSecurityManager::new)
                .withPropertyValues("sa-token.is-share=true", "sa-token.is-concurrent=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SaTokenSecurityManager.class)
                        .doesNotHaveBean("saTokenSessionPolicy"));
    }

    @Configuration(proxyBeanMethods = false)
    static class NativeWiring {
        @Bean UserStore users(SaTokenConfig config, ObjectProvider<StpLogic> custom) {
            // 与原生 SaBeanInject 一样选取最终配置，同时避免测试加载全局插件和 Web 容器。
            StpLogic logic = custom.getIfAvailable(() -> new StpLogic("login").setConfig(config));
            StpUtil.setStpLogic(logic);
            return id -> null;
        }
    }
}
