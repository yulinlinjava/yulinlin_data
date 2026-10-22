package com.yulinlin.security;

import com.yulinlin.security.aop.ApiAuthAspect;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.auth.LoginAuthenticator;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.session.LoginSessionManager;
import com.yulinlin.security.session.SessionManagementProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

/** 鉴权独立于 MVC 报文加密和限流，非 Web 项目也可保护 Service Bean。 */
@AutoConfiguration(after = AopAutoConfiguration.class)
@EnableConfigurationProperties(SessionManagementProperties.class)
public class SecurityAuthAutoConfiguration {

    @Bean
    public static BeanFactoryPostProcessor exclusiveSecurityImplementations() {
        return factory -> {
            var providers = java.util.Set.of("com.yulinlin.security.jwt.JwtAutoConfiguration",
                    "com.yulinlin.security.satoken.SaTokenAutoConfiguration",
                    "com.yulinlin.security.spring.SpringSecurityAdapterAutoConfiguration");
            long count = java.util.Arrays.stream(factory.getBeanDefinitionNames())
                    .map(name -> factory.getBeanDefinition(name).getBeanClassName())
                    .filter(name -> name != null && providers.contains(name)).count();
            if (count > 1) throw new IllegalStateException("security-jwt、security-sa-token、security-spring 只能启用一个，不能使用 @Primary 混用");
        };
    }

    @Bean
    @ConditionalOnMissingBean(LoginAuthenticatorRegistry.class)
    public LoginAuthenticatorRegistry loginAuthenticatorRegistry(ObjectProvider<LoginAuthenticator<?>> authenticators,
                                                                  ObjectProvider<UserStore> users) {
        return new LoginAuthenticatorRegistry(authenticators.orderedStream().toList(), users.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(ISecurityManager.class)
    public AnonymousSecurityManager anonymousSecurityManager() {
        return new AnonymousSecurityManager();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityUtil.class)
    public SecurityUtil securityUtil(ObjectProvider<ISecurityManager> securityManagers,
                                     ObjectProvider<LoginSessionManager> sessionManagers,
                                     SessionManagementProperties properties) {
        return new SecurityUtil(securityManagers, sessionManagers, properties);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(ApiAuthAspect.class)
    public ApiAuthAspect apiAuthAspect(ObjectProvider<ISecurityManager> securityManagers) {
        // 不在代理基础设施创建时提前实例化业务管理器，避免影响其依赖的 Bean 装配。
        return new ApiAuthAspect(securityManagers::getObject);
    }
}
