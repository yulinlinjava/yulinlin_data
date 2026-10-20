package com.yulinlin.security;

import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.web.LoginContextInterceptor;
import com.yulinlin.security.web.LoginContextWebMvcConfigurer;
import com.yulinlin.security.web.LoginInterceptor;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** 自动注册登录拦截器：优先使用业务 LoginInterceptor Bean，否则提供默认实现。 */
@AutoConfiguration(after = SecurityAuthAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityLoginAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(LoginInterceptor.class)
    public LoginContextInterceptor loginContextInterceptor(ObjectProvider<ISecurityManager> managers) {
        return new LoginContextInterceptor(managers::getObject);
    }

    @Bean
    @ConditionalOnMissingBean(LoginContextWebMvcConfigurer.class)
    public LoginContextWebMvcConfigurer loginContextWebMvcConfigurer(ObjectProvider<LoginInterceptor> interceptors) {
        try {
            return new LoginContextWebMvcConfigurer(interceptors.getObject());
        } catch (NoUniqueBeanDefinitionException error) {
            throw new IllegalStateException("存在多个 LoginInterceptor，请只提供一个登录拦截器 Bean，或通过 @Primary 明确选择", error);
        }
    }
}
