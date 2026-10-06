package com.yulinlin.jdbc.postgresql;

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
@EnableConfigurationProperties(PostgresqlProperties.class)
public class PostgresqlAutoConfiguration {
    @Bean("postgresqlSessionFactory")
    @ConditionalOnMissingBean(name = "postgresqlSessionFactory")
    public JdbcSessionFactory postgresqlSessionFactory() {
        return new JdbcSessionFactory("jdbc:postgresql:", PostgresqlSession::new);
    }

    @Bean("postgresqlSession")
    @ConditionalOnMissingBean(name = {"postgresqlSession", "jdbcSession"})
    @ConditionalOnSingleCandidate(DataSource.class)
    public JdbcSession postgresqlSession(DataSource dataSource,
            @Qualifier("postgresqlSessionFactory") JdbcSessionFactory factory,
            PostgresqlProperties properties) {
        PostgresqlSession session = (PostgresqlSession) factory.create(dataSource, "postgresql");
        session.configure(properties);
        if (properties.getSchemaMode() != PostgresqlProperties.SchemaMode.NONE) {
            session.initializeSchema(SchemaEntityScanner.scan(properties.getSchemaPackages()));
        }
        return session;
    }
}
