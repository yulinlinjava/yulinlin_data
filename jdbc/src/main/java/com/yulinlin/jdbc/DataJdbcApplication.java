package com.yulinlin.jdbc;

import com.yulinlin.jdbc.aop.SpringTransactionAop;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.log.SqlNodeLog;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties(JdbcProperties.class)
@AutoConfiguration
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
