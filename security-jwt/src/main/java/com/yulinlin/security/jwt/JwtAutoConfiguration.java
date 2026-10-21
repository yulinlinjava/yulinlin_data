package com.yulinlin.security.jwt;

import com.yulinlin.security.SecurityAuthAutoConfiguration;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.UserStore;
import com.yulinlin.security.auth.UserAuthenticator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 提供业务 ISecurityManager 时整体退让，不再强制要求 JWT 密钥或 UserStore。 */
@AutoConfiguration(before = SecurityAuthAutoConfiguration.class)
@EnableConfigurationProperties(JwtProperties.class)
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
                                                 ObjectProvider<UserAuthenticator> authenticators) {
        return new JwtSecurityManager(users, tokens, properties, authenticators.getIfAvailable());
    }
}
