package com.yulinlin.admin.rbac.config;

import com.yulinlin.admin.rbac.auth.PasswordHash;
import com.yulinlin.admin.rbac.service.RbacBootstrap;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RbacProperties.class)
public class RbacConfiguration {
    @Bean
    public PasswordHash rbacPasswordHash() { return new PasswordHash(); }

    @Bean
    public ApplicationRunner rbacBootstrapRunner(RbacBootstrap bootstrap) {
        return arguments -> bootstrap.initialize();
    }
}
