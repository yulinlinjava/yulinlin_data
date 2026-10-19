package com.yulinlin.jdbc;

import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.log.SqlNodeLog;
import com.yulinlin.jdbc.transaction.RouteTransactionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Shared JDBC infrastructure; database modules register their own sessions. */
@AutoConfiguration(before = {DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class})
@EnableTransactionManagement(order = 9999)
public class DataJdbcApplication {
    @Bean
    @ConditionalOnMissingBean
    public JdbcCoderManager jdbcCoderManager() {
        return new JdbcCoderManager();
    }

    @Bean("yulinlinTransactionManager")
    @Primary
    @ConditionalOnMissingBean(name = "yulinlinTransactionManager")
    public RouteTransactionManager yulinlinTransactionManager(ObjectProvider<RouteSession> routes) {
        return new RouteTransactionManager(routes::getObject);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlNodeLog sqlNodeLog() {
        return new SqlNodeLog();
    }
}
