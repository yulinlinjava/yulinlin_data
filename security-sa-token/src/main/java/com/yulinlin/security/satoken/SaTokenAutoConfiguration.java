package com.yulinlin.security.satoken;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.spring.SaBeanRegister;
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
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
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
    public SmartInitializingSingleton saTokenSessionPolicy(SaTokenSecurityManager manager,
            SessionManagementProperties properties, Environment environment, ConfigurableListableBeanFactory beans) {
        return () -> {
            properties.validate();
            var config = manager.stpLogic().getConfigOrGlobal();
            // 统一登录每次分配独立 Token，否则不同认证方式会覆盖共享 Token 的 authType。
            if (!Boolean.FALSE.equals(config.getIsShare())) {
                throw new IllegalStateException("统一登录要求 sa-token.is-share=false；共享 Token 请自定义 ISecurityManager");
            }
            if (!properties.isEnabled(true)) return;
            Integer nativeMax = Binder.get(environment).bind("sa-token.max-login-count", Integer.class).orElse(null);
            if (nativeMax != null && nativeMax != properties.getMaxSessions()) {
                throw new IllegalStateException("sa-token.max-login-count 与 yulinlin.security.session.max-sessions 冲突，请只维护统一配置");
            }
            if (!Boolean.TRUE.equals(config.getIsConcurrent())) {
                throw new IllegalStateException("统一会话管理要求 sa-token.is-concurrent=true，不能同时启用原生互斥登录");
            }
            // 仅允许替换官方自动装配的未配置默认值。Java Bean / StpLogic 的配置不能被悄悄覆盖，
            // 包括用户显式选择了与官方默认值相同的上限的情况。
            boolean untouchedDefault = nativeMax == null
                    && config.getMaxLoginCount() == new SaTokenConfig().getMaxLoginCount()
                    && isOfficialConfig(config, beans);
            if (!untouchedDefault && config.getMaxLoginCount() != properties.getMaxSessions()) {
                throw new IllegalStateException("SaTokenConfig/StpLogic 的 maxLoginCount 与 yulinlin.security.session.max-sessions 冲突；"
                        + "自定义原生配置时请显式设置相同上限，或关闭统一会话管理");
            }
            config.setMaxLoginCount(properties.getMaxSessions());
        };
    }

    private static boolean isOfficialConfig(SaTokenConfig config, ConfigurableListableBeanFactory beans) {
        for (String name : beans.getBeanNamesForType(SaTokenConfig.class, false, false)) {
            if (!beans.containsBeanDefinition(name)) continue;
            var definition = beans.getBeanDefinition(name);
            String factory = definition.getFactoryBeanName();
            if (factory != null && "getSaTokenConfig".equals(definition.getFactoryMethodName())
                    && beans.getType(factory, false) == SaBeanRegister.class && beans.getBean(name) == config) {
                return true;
            }
        }
        return false;
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
