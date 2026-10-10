package com.yulinlin.repository;

import com.yulinlin.repository.proxy.MethodParseManager;
import com.yulinlin.repository.session.RepositorySession;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(RepositoryProperties.class)
public class RepositoryAutoConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static RepositoryScannerConfigurer repositoryScannerConfigurer(Environment environment,
                                                                    BeanFactory beanFactory) {
        List<String> packages = Binder.get(environment)
                .bind("yulinlin.repository.scan-packages", Bindable.listOf(String.class))
                .orElseGet(List::of);
        if (packages.stream().allMatch(value -> value == null || value.isBlank())) {
            if (!AutoConfigurationPackages.has(beanFactory)) {
                throw new IllegalStateException("Cannot determine Repository scan packages. Configure "
                        + "yulinlin.repository.scan-packages or start from a Spring Boot application package.");
            }
            packages = AutoConfigurationPackages.get(beanFactory);
        }
        return new RepositoryScannerConfigurer(packages.toArray(String[]::new));
    }

    @ConditionalOnMissingBean
    @Bean
    public MethodParseManager repositoryMethodParseManager(){
        return new MethodParseManager();
    }

    @ConditionalOnMissingBean
    @Bean
    public RepositorySession repositoryFactory(MethodParseManager methodParseManager){
        return new RepositorySession(methodParseManager);
    }

}
