package com.yulinlin.jdbc;

import com.yulinlin.jdbc.aop.SpringTransactionAop;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.log.SqlNodeLog;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Shared JDBC infrastructure; database modules register their own sessions. */
@AutoConfiguration
public class DataJdbcApplication {
    @Bean
    @ConditionalOnMissingBean
    public JdbcCoderManager jdbcCoderManager() {
        return new JdbcCoderManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringTransactionAop springTransactionAop() {
        return new SpringTransactionAop();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlNodeLog sqlNodeLog() {
        return new SqlNodeLog();
    }
}
