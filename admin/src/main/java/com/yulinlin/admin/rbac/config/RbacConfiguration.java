package com.yulinlin.admin.rbac.config;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.admin.rbac.auth.RbacInterceptor;
import com.yulinlin.admin.rbac.service.RbacBootstrap;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@Profile("rbac")
@EnableConfigurationProperties(RbacProperties.class)
public class RbacConfiguration {
    @Bean
    public PasswordHash rbacPasswordHash() { return new PasswordHash(); }

    @Bean
    public WebMvcConfigurer rbacWebMvcConfigurer(AdminSecurityManager security) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RbacInterceptor(security)).addPathPatterns("/rbac/**").order(0);
            }
        };
    }

    @Bean
    public ApplicationRunner rbacBootstrapRunner(RbacBootstrap bootstrap) {
        return arguments -> bootstrap.initialize();
    }
}
