package com.yulinlin.security.satoken;

import cn.dev33.satoken.stp.StpUtil;
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
import org.springframework.core.env.Environment;

/** 应用必须只选择一种安全实现；业务 ISecurityManager 可替换默认实现。 */
@AutoConfiguration(before = {SecurityAuthAutoConfiguration.class, SecurityLoginAutoConfiguration.class})
@EnableConfigurationProperties(SessionManagementProperties.class)
@ConditionalOnMissingBean(value = ISecurityManager.class, ignoredType = {
        "com.yulinlin.security.jwt.JwtSecurityManager",
        "com.yulinlin.security.satoken.SaTokenSecurityManager",
        "com.yulinlin.security.spring.SpringSecurityManager"
})
public class SaTokenAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(LoginSessionManager.class)
    public SaTokenLoginSessionManager saTokenLoginSessionManager(SaTokenSecurityManager manager,
                                                                SessionManagementProperties properties) {
        return new SaTokenLoginSessionManager(manager::stpLogic, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "yulinlin.security.session", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SmartInitializingSingleton saTokenSessionPolicy(SaTokenSecurityManager manager,
                                                           SessionManagementProperties properties, Environment environment) {
        return () -> {
            properties.validate();
            Integer nativeMax = environment.getProperty("sa-token.max-login-count", Integer.class);
            if (nativeMax != null && nativeMax != properties.getMaxSessions()) {
                throw new IllegalStateException("sa-token.max-login-count 与统一 max-sessions 冲突，请只维护统一配置");
            }
            var config = manager.stpLogic().getConfigOrGlobal();
            if (!Boolean.TRUE.equals(config.getIsConcurrent())) {
                throw new IllegalStateException("统一会话管理要求 sa-token.is-concurrent=true，不能同时启用原生互斥登录");
            }
            config.setMaxLoginCount(properties.getMaxSessions());
        };
    }
    @Bean
    public SaTokenSecurityManager saTokenSecurityManager(UserStore users, LoginAuthenticatorRegistry authenticators) {
        return new SaTokenSecurityManager(users, StpUtil::getStpLogic, authenticators);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(LoginInterceptor.class)
    public SaTokenLoginInterceptor saTokenLoginInterceptor(ObjectProvider<ISecurityManager> managers) {
        return new SaTokenLoginInterceptor(managers::getObject);
    }
}
