package com.yulinlin.security.spring;

import com.yulinlin.security.session.SessionManagementProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

/** 显式接入一个有状态过滤器链；捕获该链原生策略供统一 MVC 登录使用，不另造并发控制。 */
public final class SpringSessionSupport implements SessionAuthenticationStrategy, SmartInitializingSingleton {
    private final SessionRegistry registry;
    private final SessionManagementProperties properties;
    private final SecurityContextRepository repository;
    private volatile SessionAuthenticationStrategy delegate;
    private boolean configured;

    public SpringSessionSupport(SessionRegistry registry, SessionManagementProperties properties,
                                SecurityContextRepository repository) {
        this.registry = registry;
        this.properties = properties;
        this.repository = repository;
    }

    /** 在业务 http.build() 前最后调用；其他无状态链无需接入。不要再次覆盖会话配置。 */
    public synchronized void configure(HttpSecurity http) throws Exception {
        if (configured) throw new IllegalStateException("SpringSessionSupport 只能绑定一个有状态过滤器链；多会话体系请自定义适配");
        properties.validate();
        http.securityContext(context -> context.securityContextRepository(repository));
        http.sessionManagement(session -> session.maximumSessions(properties.getMaxSessions())
                .maxSessionsPreventsLogin(false).sessionRegistry(registry)
                .expiredSessionStrategy(event -> event.getResponse().sendError(401, "登录会话已失效")));
        http.with(new Capture(), configurer -> { });
        configured = true;
    }

    private final class Capture extends AbstractHttpConfigurer<Capture, HttpSecurity> {
        @Override public void configure(HttpSecurity http) {
            SessionAuthenticationStrategy strategy = http.getSharedObject(SessionAuthenticationStrategy.class);
            if (strategy == null || strategy == SpringSessionSupport.this) {
                throw new IllegalStateException("未获得原生会话认证策略，不能使用 SpringSessionSupport 替换链内部策略");
            }
            if (http.getSharedObject(SecurityContextRepository.class) != repository) {
                throw new IllegalStateException("统一登录与过滤器链必须共用 SecurityContextRepository");
            }
            delegate = strategy;
        }
    }

    @Override public void afterSingletonsInstantiated() {
        if (delegate == null) throw new IllegalStateException("已开启统一会话管理，请在业务 SecurityFilterChain 构建前调用 SpringSessionSupport.configure(http)");
    }

    @Override public void onAuthentication(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SessionAuthenticationStrategy strategy = delegate;
        if (strategy == null) throw new IllegalStateException("Spring 会话过滤器链尚未接入");
        strategy.onAuthentication(authentication, request, response);
    }

    /** 策略已注册但持久化失败时，不留下幽灵会话。 */
    void loginFailed(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            registry.removeSessionInformation(session.getId());
            session.invalidate();
        }
    }
}
