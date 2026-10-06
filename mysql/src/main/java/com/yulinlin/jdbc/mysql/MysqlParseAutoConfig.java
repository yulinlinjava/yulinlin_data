package com.yulinlin.jdbc.mysql;

import com.yulinlin.jdbc.DataJdbcApplication;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import com.yulinlin.jdbc.schema.SchemaEntityScanner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

@AutoConfiguration(after = {DataSourceAutoConfiguration.class, DataJdbcApplication.class})
@EnableConfigurationProperties(MysqlProperties.class)
public class MysqlParseAutoConfig {
    @Bean("mysqlSessionFactory")
    @ConditionalOnMissingBean(name = "mysqlSessionFactory")
    public JdbcSessionFactory mysqlSessionFactory() {
        return new JdbcSessionFactory("jdbc:mysql:", MysqlSession::new);
    }

    @Bean("mysqlSession")
    @ConditionalOnMissingBean(name = {"mysqlSession", "jdbcSession"})
    @ConditionalOnSingleCandidate(DataSource.class)
    public JdbcSession mysqlSession(DataSource dataSource,
            @Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            MysqlProperties properties) {
        MysqlSession session = (MysqlSession) factory.create(dataSource, "mysql");
        session.configure(properties);
        if (properties.getSchemaMode() != MysqlProperties.SchemaMode.NONE) {
            session.initializeSchema(SchemaEntityScanner.scan(properties.getSchemaPackages()));
        }
        return session;
    }
}
