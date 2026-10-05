package com.yulinlin.jdbc.sqlite;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(after = DataSourceAutoConfiguration.class,
        beforeName = "com.yulinlin.jdbc.mysql.MysqlParseAutoConfig")
@EnableConfigurationProperties(SqliteProperties.class)
public class SqliteAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SqliteDatabase.class)
    public SqliteDatabase sqliteDatabase(SqliteProperties properties, Environment environment) {
        if ("primary".equals(properties.getGroup()) && (environment.containsProperty("spring.datasource.url")
                || environment.containsProperty("spring.datasource.jndi-name"))) {
            throw new IllegalArgumentException("With a main DataSource, set yulinlin.sqlite.group=sqlite (not primary)");
        }
        return new SqliteDatabase(properties);
    }

    @Bean("sqliteSessionFactory")
    @ConditionalOnMissingBean(name = "sqliteSessionFactory")
    public JdbcSessionFactory sqliteSessionFactory() { return new JdbcSessionFactory("jdbc:sqlite:", SqliteSession::new); }

    @Bean("sqliteSession")
    public JdbcSession sqliteSession(@Qualifier("sqliteSessionFactory") JdbcSessionFactory factory, SqliteProperties properties,
                                      SqliteDatabase database) {
        JdbcSession session = factory.create(database.dataSource(), properties.getGroup());
        session.setParallelConnections(1); // WAL still permits only one simultaneous writer per file.
        return session;
    }

}
