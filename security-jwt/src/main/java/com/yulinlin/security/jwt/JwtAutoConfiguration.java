package com.yulinlin.security.jwt;

import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.LoginAuthenticatorRegistry;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.session.SessionManagementProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 提供业务 ISecurityManager 时整体退让，不再强制要求 JWT 密钥或 UserStore。 */
@AutoConfiguration(before = SecurityAuthAutoConfiguration.class)
@EnableConfigurationProperties({JwtProperties.class, SessionManagementProperties.class})
@ConditionalOnMissingBean(value = ISecurityManager.class, ignoredType = {
        "com.yulinlin.security.jwt.JwtSecurityManager",
        "com.yulinlin.security.satoken.SaTokenSecurityManager",
        "com.yulinlin.security.spring.SpringSecurityManager"
})
public class JwtAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(JwtTokenCodec.class)
    public DefaultJwtTokenCodec jwtTokenCodec(JwtProperties properties) {
        return new DefaultJwtTokenCodec(properties);
    }

    @Bean
    public JwtSecurityManager jwtSecurityManager(UserStore users, JwtTokenCodec tokens, JwtProperties properties,
                                                 LoginAuthenticatorRegistry authenticators,
                                                 SessionManagementProperties sessions) {
        if (sessions.isExplicitlyEnabled()) throw new IllegalStateException("security-jwt 为无状态实现，不支持 yulinlin.security.session.enabled=true");
        return new JwtSecurityManager(users, tokens, properties, authenticators);
    }
}
