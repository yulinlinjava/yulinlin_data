package com.yulinlin.security;

import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.aop.ApiAuthAspect;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只验证 Java Bean 自动装配，不加载业务数据库、MVC 加密或 YAML。 */
class SecurityAuthAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, SecurityAuthAutoConfiguration.class))
            .withBean(ProtectedService.class, ProtectedService::new)
            .withBean(PlainService.class, PlainService::new);

    @Test void defaultAnonymousManagerWorksWithoutWebOrCryptoSettings() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ISecurityManager.class).hasSingleBean(ApiAuthAspect.class);
            assertThat(context.getBean(ISecurityManager.class)).isInstanceOf(AnonymousSecurityManager.class);
            assertThat(context).doesNotHaveBean(SecurityCryptoProperties.class);
            assertThat(context.getBean(PlainService.class).read()).isEqualTo("plain");
            assertThat(AopUtils.isAopProxy(context.getBean(PlainService.class))).isFalse();
            assertThat(context.getBean(ProtectedService.class).login()).isEqualTo("public");
            assertThatThrownBy(() -> context.getBean(ProtectedService.class).read())
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
        });
    }

    @Test void customManagerReplacesTheFallbackAndIsOnlyConsultedForProtectedCalls() {
        ISecurityManager manager = mock(ISecurityManager.class);
        when(manager.getUserDetails()).thenReturn(mock(IUserDetails.class));
        runner.withBean(ISecurityManager.class, () -> manager).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ISecurityManager.class)
                    .doesNotHaveBean(AnonymousSecurityManager.class);
            assertThat(context.getBean(ISecurityManager.class)).isSameAs(manager);
            context.getBean(PlainService.class).read();
            context.getBean(ProtectedService.class).login();
            verifyNoInteractions(manager);
            assertThat(context.getBean(ProtectedService.class).read()).isEqualTo("protected");
            verify(manager).getUserDetails();
        });
    }

    @Test void finalProtectedMethodsAreRejectedEvenWhenAnotherMethodMatchesFirst() {
        runner.withBean(InvalidFinalService.class, InvalidFinalService::new).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("不能是 final");
        });
    }

    @ApiLogin
    public static class ProtectedService {
        @ApiPublic public String login() { return "public"; }
        public String read() { return "protected"; }
    }

    public static class PlainService {
        public String read() { return "plain"; }
    }

    @ApiLogin
    public static class InvalidFinalService {
        public String read() { return "protected"; }
        public final String finalRead() { return "cannot proxy"; }
    }
}
