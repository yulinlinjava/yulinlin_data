package com.yulinlin.repository;

import com.yulinlin.repository.proxy.MethodParseManager;
import com.yulinlin.repository.session.RepositorySession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

public class RepositoryAutoConfig {

    @ConditionalOnMissingBean
    @Bean
    public RepositorySession repositoryFactory(){
        return new RepositorySession( new MethodParseManager());
    }

}
