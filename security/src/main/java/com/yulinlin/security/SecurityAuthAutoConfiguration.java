package com.yulinlin.security;

import com.yulinlin.security.aop.ApiAuthAspect;
import com.yulinlin.security.auth.AnonymousSecurityManager;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.SecurityUtil;
import com.yulinlin.security.auth.LoginAuthenticator;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.UserStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/** 鉴权独立于 MVC 报文加密和限流，非 Web 项目也可保护 Service Bean。 */
@AutoConfiguration(after = AopAutoConfiguration.class)
public class SecurityAuthAutoConfiguration {

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
    public SecurityUtil securityUtil(ObjectProvider<ISecurityManager> securityManagers) {
        return new SecurityUtil(securityManagers);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(ApiAuthAspect.class)
    public ApiAuthAspect apiAuthAspect(ObjectProvider<ISecurityManager> securityManagers) {
        // 不在代理基础设施创建时提前实例化业务管理器，避免影响其依赖的 Bean 装配。
        return new ApiAuthAspect(securityManagers::getObject);
    }
}
