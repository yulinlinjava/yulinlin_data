package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.jdbc.DataJdbcApplication;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression cases for module-owned session registration; no server is required. */
class MysqlParseAutoConfigTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                DataJdbcApplication.class, MysqlParseAutoConfig.class))
                .withUserConfiguration(Infrastructure.class);
    }

    @Test void mysqlModuleCreatesNamedSessionWithoutBorrowingConnection() {
        runner().withUserConfiguration(PrimaryDataSource.class)
                .withPropertyValues(
                        "yulinlin.mysql.log=true",
                        "yulinlin.mysql.map-underscore-to-camel-case=false",
                        "yulinlin.mysql.parallel-connections=2",
                        "yulinlin.mysql.execute-batch-size=64")
                .run(context -> {
            assertThat(context).hasNotFailed().hasBean("mysqlSessionFactory").hasBean("mysqlSession");
            var session = context.getBean("mysqlSession", JdbcSession.class);
            var properties = context.getBean(MysqlProperties.class);
            assertThat(session).isExactlyInstanceOf(MysqlSession.class);
            assertThat(session.getParseManager()).isExactlyInstanceOf(MysqlParseManager.class);
            assertThat(session.group()).isEqualTo("mysql");
            assertThat(properties.isLog()).isTrue();
            assertThat(properties.isMapUnderscoreToCamelCase()).isFalse();
            assertThat(session.getProperties()).isSameAs(properties);
            assertThat(session.getParallelConnections()).isEqualTo(2);
            assertThat(session.getExecuteBatchSize()).isEqualTo(64);
            assertThat(context).doesNotHaveBean("jdbcSession");
            assertThat(context.getBean(HikariDataSource.class).getHikariPoolMXBean()).isNull();
        });
    }

    @Test void moduleCreationDoesNotDependOnSpringUrlProperty() {
        runner().withUserConfiguration(PrimaryDataSource.class)
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/not_actual").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("mysqlSession", JdbcSession.class).getParseManager())
                            .isExactlyInstanceOf(MysqlParseManager.class);
                });
    }

    @Test void factoryRemainsAvailableWithoutDataSource() {
        runner().run(context -> assertThat(context).hasNotFailed()
                .hasBean("mysqlSessionFactory").doesNotHaveBean("mysqlSession"));
    }

    @Test void schemaCreationIsOptInAndDoesNotBorrowAConnectionByDefault() {
        var properties = new MysqlProperties();
        assertThat(properties.getSchemaMode()).isEqualTo(SchemaMode.NONE);
        assertThat(properties.getSchemaPackages()).isEmpty();
    }

    @Test void createsSessionWithoutUrlAccessorOrBorrowingConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        runner().withBean("dataSource", DataSource.class, () -> dataSource).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("mysqlSession", JdbcSession.class).getParseManager())
                    .isExactlyInstanceOf(MysqlParseManager.class);
        });
        verify(dataSource, never()).getConnection();
    }

    @Test void explicitLegacyJdbcSessionDisablesModuleDefault() {
        runner().withUserConfiguration(PrimaryDataSource.class, ExplicitLegacySession.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean("mysqlSession");
            assertThat(context.getBean("jdbcSession", JdbcSession.class).group()).isEqualTo("manual");
        });
    }

    @Test void explicitModuleNamedSessionDisablesDefault() {
        runner().withUserConfiguration(PrimaryDataSource.class, ExplicitMysqlSession.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("mysqlSession", JdbcSession.class).group()).isEqualTo("manual-mysql");
            assertThat(context.getBeansOfType(EntitySession.class)).containsOnlyKeys("mysqlSession");
        });
    }

    @Test void onlyFactoryDoesNotCauseJdbcInfrastructureToCreateSession() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataJdbcApplication.class))
                .withUserConfiguration(Infrastructure.class, PrimaryDataSource.class, FactoryOnly.class).run(context -> {
                    assertThat(context).hasNotFailed().hasBean("customFactory");
                    assertThat(context.getBeansOfType(EntitySession.class)).isEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Infrastructure {
        @Bean IFilterManager filterManager() { return mock(IFilterManager.class); }
        @Bean EntityProxyService entityProxyService() { return mock(EntityProxyService.class); }
        @Bean DbCache dbCacheManager() { return mock(DbCache.class); }
        @Bean LogManager logManager() { return new LogManager(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryDataSource {
        @Bean HikariDataSource dataSource(Environment environment) {
            var dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(environment.getProperty("test.jdbc-url", "jdbc:mysql://localhost/test"));
            return dataSource;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExplicitLegacySession {
        @Bean JdbcSession jdbcSession(DataSource dataSource) {
            var session = new JdbcSession(dataSource); session.setGroup("manual"); return session;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExplicitMysqlSession {
        @Bean JdbcSession mysqlSession(DataSource dataSource) {
            var session = new JdbcSession(dataSource); session.setGroup("manual-mysql"); return session;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FactoryOnly {
        @Bean JdbcSessionFactory customFactory() { return new JdbcSessionFactory(new MysqlParseManager()); }
    }
}
