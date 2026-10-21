package com.yulinlin.security.spring;

import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.SecurityLoginAutoConfiguration;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.web.LoginInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.util.List;

/** 只适配现有 Spring Security，不创建认证提供者、用户表或 SecurityFilterChain。 */
@AutoConfiguration(before = {SecurityAuthAutoConfiguration.class, SecurityLoginAutoConfiguration.class})
@ConditionalOnMissingBean(value = ISecurityManager.class, ignoredType = {
        "com.yulinlin.security.jwt.JwtSecurityManager",
        "com.yulinlin.security.satoken.SaTokenSecurityManager",
        "com.yulinlin.security.spring.SpringSecurityManager"
})
public class SpringSecurityAdapterAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(SpringUserMapper.class)
    public DefaultSpringUserMapper springUserMapper() {
        return new DefaultSpringUserMapper();
    }

    @Bean
    public SpringSecurityManager springSecurityManager(SpringUserMapper mapper, ObjectProvider<UserStore> users,
            ObjectProvider<AuthenticationManager> authenticationManagers,
            ObjectProvider<SecurityContextRepository> repositories,
            ObjectProvider<SessionAuthenticationStrategy> sessionStrategies) {
        return new SpringSecurityManager(mapper, users::getIfAvailable, authenticationManagers::getIfAvailable,
                repositories::getIfAvailable, sessionStrategies::getIfAvailable);
    }

    /** 默认用于 MVC 账号密码登录；自定义链必须显式共用同一个存储 Bean。 */
    @Bean
    @ConditionalOnMissingBean(SecurityContextRepository.class)
    public SecurityContextRepository springLoginSecurityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** 不替业务启用并发会话控制或 Remember-Me；复杂策略提供同类型 Bean 替换。 */
    @Bean
    @ConditionalOnMissingBean(SessionAuthenticationStrategy.class)
    public SessionAuthenticationStrategy springLoginSessionAuthenticationStrategy(ObjectProvider<CsrfTokenRepository> csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrf.getIfAvailable(HttpSessionCsrfTokenRepository::new))));
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(LoginInterceptor.class)
    public SpringLoginInterceptor springLoginInterceptor() {
        return new SpringLoginInterceptor();
    }
}
