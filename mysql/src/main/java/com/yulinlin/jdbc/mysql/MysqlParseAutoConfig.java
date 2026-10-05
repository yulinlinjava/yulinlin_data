package com.yulinlin.jdbc.mysql;

import com.yulinlin.jdbc.DataJdbcApplication;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

@AutoConfiguration(after = {DataSourceAutoConfiguration.class, DataJdbcApplication.class})
public class MysqlParseAutoConfig {
    @Bean("mysqlSessionFactory")
    @ConditionalOnMissingBean(name = "mysqlSessionFactory")
    public JdbcSessionFactory mysqlSessionFactory() {
        return new JdbcSessionFactory(new MysqlParseManager());
    }

    @Bean("mysqlSession")
    @ConditionalOnMissingBean(name = {"mysqlSession", "jdbcSession"})
    @ConditionalOnSingleCandidate(DataSource.class)
    public JdbcSession mysqlSession(DataSource dataSource,
            @Qualifier("mysqlSessionFactory") JdbcSessionFactory factory) {
        return factory.create(dataSource, "mysql");
    }
}
