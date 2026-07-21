package com.yulinlin.jdbc;

import com.yulinlin.jdbc.aop.SpringTransactionAop;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.log.SqlNodeLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(JdbcProperties.class)
@Configuration
public class DataJdbcApplication {

    @ConditionalOnMissingBean
    @Bean
    public JdbcCoderManager jdbcCoderManager(){
        return  new JdbcCoderManager();
    }


    @ConditionalOnMissingBean
    @Bean
    public SpringTransactionAop springTransactionAop(){
        return  new SpringTransactionAop();
    }



    @ConditionalOnMissingBean
    @Bean
    public SqlNodeLog sqlNodeLog() {
        return new SqlNodeLog();
    }







}
