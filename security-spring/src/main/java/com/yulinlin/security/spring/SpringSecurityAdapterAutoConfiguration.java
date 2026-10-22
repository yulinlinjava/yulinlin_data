package com.yulinlin.security.spring;

import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.SecurityLoginAutoConfiguration;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.session.LoginSessionManager;
import com.yulinlin.security.session.SessionManagementProperties;
import com.yulinlin.security.web.LoginInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
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

/** 适配现有 Spring Security，不创建 AuthenticationManager、用户表或过滤器链；会话管理需业务显式接入。 */
@AutoConfiguration(before = {SecurityAuthAutoConfiguration.class, SecurityLoginAutoConfiguration.class})
@EnableConfigurationProperties(SessionManagementProperties.class)
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
            LoginAuthenticatorRegistry authenticators,
            ObjectProvider<AuthenticationManager> authenticationManagers,
            ObjectProvider<SecurityContextRepository> repositories,
            ObjectProvider<SessionAuthenticationStrategy> sessionStrategies) {
        return new SpringSecurityManager(mapper, users::getIfAvailable, authenticators, authenticationManagers::getIfAvailable,
                repositories::getIfAvailable, sessionStrategies::getIfAvailable);
    }

    /** 仅提供桥接 Provider Bean；由业务显式加入其 AuthenticationManager，不修改已有过滤器链。 */
    @Bean
    @ConditionalOnMissingBean(LoginAuthenticatorAuthenticationProvider.class)
    public LoginAuthenticatorAuthenticationProvider loginAuthenticatorAuthenticationProvider(LoginAuthenticatorRegistry authenticators) {
        return new LoginAuthenticatorAuthenticationProvider(authenticators);
    }

    /** 默认用于 MVC 账号密码登录；自定义链必须显式共用同一个存储 Bean。 */
    @Bean
    @ConditionalOnMissingBean(SecurityContextRepository.class)
    public SecurityContextRepository springLoginSecurityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** 显式关闭统一会话管理时，保留原有登录防护，不启用并发会话控制或 Remember-Me。 */
    @Bean
    @ConditionalOnMissingBean(SessionAuthenticationStrategy.class)
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "false")
    public SessionAuthenticationStrategy springLoginSessionAuthenticationStrategy(ObjectProvider<CsrfTokenRepository> csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrf.getIfAvailable(HttpSessionCsrfTokenRepository::new))));
    }

    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(value = SessionRegistry.class, type = "org.springframework.session.SessionRepository")
    public SessionRegistryImpl springLoginSessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(HttpSessionEventPublisher.class)
    public HttpSessionEventPublisher springSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({SpringSessionPrincipalResolver.class, LoginSessionManager.class})
    public SpringSessionPrincipalResolver springSessionPrincipalResolver(SpringUserMapper mapper) {
        if (!(mapper instanceof DefaultSpringUserMapper)) {
            throw new IllegalStateException("自定义 SpringUserMapper 时请同时提供 SpringSessionPrincipalResolver，保证会话 userId 映射一致");
        }
        return SpringSessionPrincipalResolver.defaults();
    }

    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(LoginSessionManager.class)
    public SpringLoginSessionManager springLoginSessionManager(SessionRegistry registry,
            SpringSessionPrincipalResolver principals, SessionManagementProperties properties) {
        return new SpringLoginSessionManager(registry, principals, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SpringSessionSupport springSessionSupport(SessionRegistry registry, SessionManagementProperties properties,
                                                       SecurityContextRepository repository) {
        return new SpringSessionSupport(registry, properties, repository);
    }

    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SmartInitializingSingleton springSessionStrategyValidation(ObjectProvider<SessionAuthenticationStrategy> strategies) {
        return () -> {
            if (strategies.stream().count() != 1) {
                throw new IllegalStateException("统一会话管理应共用 SpringSessionSupport；请在过滤器链定制原生策略，不要另注册 SessionAuthenticationStrategy");
            }
        };
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(LoginInterceptor.class)
    public SpringLoginInterceptor springLoginInterceptor() {
        return new SpringLoginInterceptor();
    }
}
