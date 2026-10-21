package com.yulinlin.security.satoken;

import cn.dev33.satoken.stp.StpUtil;
import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.SecurityLoginAutoConfiguration;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.auth.UserAuthenticator;
import com.yulinlin.security.web.LoginInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** 与 JWT 实现并存时不静默抢占；应用必须只选一种，或提供自己的 @Primary 管理器。 */
@AutoConfiguration(before = {SecurityAuthAutoConfiguration.class, SecurityLoginAutoConfiguration.class})
@ConditionalOnMissingBean(value = ISecurityManager.class, ignoredType = {
        "com.yulinlin.security.jwt.JwtSecurityManager",
        "com.yulinlin.security.satoken.SaTokenSecurityManager",
        "com.yulinlin.security.spring.SpringSecurityManager"
})
public class SaTokenAutoConfiguration {
    @Bean
    public SaTokenSecurityManager saTokenSecurityManager(UserStore users, ObjectProvider<UserAuthenticator> authenticators) {
        return new SaTokenSecurityManager(users, StpUtil::getStpLogic, authenticators.getIfAvailable());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(LoginInterceptor.class)
    public SaTokenLoginInterceptor saTokenLoginInterceptor(ObjectProvider<ISecurityManager> managers) {
        return new SaTokenLoginInterceptor(managers::getObject);
    }
}
