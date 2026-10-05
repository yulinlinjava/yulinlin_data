package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.jdbc.DataJdbcApplication;
import com.yulinlin.jdbc.session.JdbcSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No real connection or default database file is created by these cases. */
class SqliteDefaultsTest {
    @Test void propertiesProvideDefaultFileAndNamedGroup() {
        var properties = new SqliteProperties();
        assertThat(properties.getFile()).isEqualTo("data/local.db");
        assertThat(properties.getGroup()).isEqualTo("sqlite");
        assertThat(properties.getSchema().isEnabled()).isFalse();
    }

    @Test void noYamlFilePropertyStillCreatesConfiguredSessionWithoutTouchingDisk() throws Exception {
        var dataSource = mock(SqliteDataSource.class);
        var database = mock(SqliteDatabase.class);
        when(database.dataSource()).thenReturn(dataSource);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                DataJdbcApplication.class, SqliteAutoConfiguration.class))
                .withInitializer(context -> {
                    context.getEnvironment().getPropertySources().remove(
                            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources().remove(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                })
                .withUserConfiguration(Infrastructure.class)
                .withBean(SqliteDatabase.class, () -> database)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(SqliteProperties.class);
                    assertThat(properties.getFile()).isEqualTo("data/local.db");
                    assertThat(properties.getGroup()).isEqualTo("sqlite");
                    var session = context.getBean("sqliteSession", JdbcSession.class);
                    assertThat(session.group()).isEqualTo("sqlite");
                    assertThat(session.getParallelConnections()).isEqualTo(1);
                });
        verify(dataSource, never()).getConnection();
    }

    @Test void sqliteModuleExcludesBootDataSourceWhenNoMainDatabaseIsConfigured() {
        assertThat(filter()).containsExactly(false, true);
    }

    @Test void explicitMainUrlOrJndiKeepsBootDataSourceConfiguration() {
        assertThat(filter("spring.datasource.url=jdbc:mysql://localhost/test")).containsExactly(true, true);
        assertThat(filter("spring.datasource.jndi-name=java:comp/env/jdbc/main")).containsExactly(true, true);
    }

    private boolean[] filter(String... properties) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        TestPropertyValues.of(properties).applyTo(environment);
        var filter = new SqliteOnlyAutoConfigurationFilter();
        filter.setEnvironment(environment);
        return filter.match(new String[]{
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "com.yulinlin.jdbc.DataJdbcApplication"}, null);
    }

    @Configuration(proxyBeanMethods = false)
    static class Infrastructure {
        @Bean IFilterManager filterManager() { return mock(IFilterManager.class); }
        @Bean EntityProxyService entityProxyService() { return mock(EntityProxyService.class); }
        @Bean DbCache dbCacheManager() { return mock(DbCache.class); }
        @Bean LogManager logManager() { return new LogManager(); }
    }
}
