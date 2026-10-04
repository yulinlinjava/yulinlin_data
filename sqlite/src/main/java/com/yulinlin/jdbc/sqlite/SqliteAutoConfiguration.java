package com.yulinlin.jdbc.sqlite;

import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(after = DataSourceAutoConfiguration.class,
        beforeName = "com.yulinlin.jdbc.mysql.MysqlParseAutoConfig")
@ConditionalOnProperty(prefix = "yulinlin.sqlite", name = "file")
@EnableConfigurationProperties(SqliteProperties.class)
public class SqliteAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SqliteDatabase.class)
    public SqliteDatabase sqliteDatabase(SqliteProperties properties, Environment environment) {
        if ("primary".equals(properties.getGroup()) && (environment.containsProperty("spring.datasource.url")
                || environment.containsProperty("spring.datasource.jndi-name"))) {
            throw new IllegalArgumentException("With a main DataSource, set yulinlin.sqlite.group=local (not primary)");
        }
        return new SqliteDatabase(properties);
    }

    @Bean("sqliteSessionFactory")
    @ConditionalOnMissingBean(name = "sqliteSessionFactory")
    public JdbcSessionFactory sqliteSessionFactory() { return new JdbcSessionFactory(new SqliteParseManager()); }

    @Bean
    @ConditionalOnMissingBean(SqliteSchemaManager.class)
    public SqliteSchemaManager sqliteSchemaManager(SqliteDatabase database, SqliteProperties properties, JdbcProperties jdbc) {
        var manager = new SqliteSchemaManager(database.dataSource(), jdbc.isMapUnderscoreToCamelCase());
        if (properties.getSchema().isEnabled()) {
            manager.scanAndCreate(properties.getSchema().getPackages().toArray(String[]::new));
        }
        return manager;
    }

    @Bean("sqliteSession")
    public JdbcSession sqliteSession(@Qualifier("sqliteSessionFactory") JdbcSessionFactory factory, SqliteProperties properties,
                                      SqliteDatabase database, SqliteSchemaManager schemaManager) {
        JdbcSession session = factory.create(database.dataSource(), properties.getGroup());
        session.setParallelConnections(1); // WAL still permits only one simultaneous writer per file.
        return session;
    }

}
