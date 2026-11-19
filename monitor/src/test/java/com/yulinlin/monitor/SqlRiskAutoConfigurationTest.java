package com.yulinlin.monitor;

import com.yulinlin.data.core.cache.CacheRegistry;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.jdbc.mysql.MysqlSession;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import com.yulinlin.jdbc.session.JdbcSqlGuard;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SqlRiskAutoConfigurationTest {
    private final ApplicationContextRunner base = new ApplicationContextRunner()
            .withBean(CacheRegistry.class, () -> new CacheRegistry(List.of(), null))
            .withBean(EntityProxyService.class, EntityProxyService::newInstance);
    private final ApplicationContextRunner runner = base.withConfiguration(AutoConfigurations.of(MonitorAutoConfiguration.class));

    @Test void defaultsToBlockAndIsWiredIntoMysqlFactory() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SqlRiskDetector.class).hasSingleBean(JdbcSqlGuard.class);
            assertEquals(MonitorProperties.SqlRiskMode.BLOCK, context.getBean(MonitorProperties.class).getSqlRisk().getMode());
            var session = session(context.getSourceApplicationContext(), "jdbc:mysql:");
            assertThrows(SqlRiskException.class,
                    () -> session.select(SqlRiskIntegrationTest.query("SELECT SLEEP(?)", Map.of())));
            assertEquals(1, context.getBean(SqlRiskMonitor.class).recent(10).size());
        });
    }

    @Test void customBeanReplacesDruidAndRunsOnMysqlOnly() {
        runner.withUserConfiguration(Custom.class).run(context -> {
            assertThat(context).hasSingleBean(SqlRiskDetector.class).doesNotHaveBean(DruidSqlRiskDetector.class);
            var mysql = session(context.getSourceApplicationContext(), "jdbc:mysql:");
            assertThrows(SqlRiskException.class,
                    () -> mysql.select(SqlRiskIntegrationTest.query("SELECT 1", Map.of())));
            for (String dialect : List.of("jdbc:h2:", "jdbc:postgresql:", "jdbc:sqlite:")) {
                var other = session(context.getSourceApplicationContext(), dialect);
                assertEquals(1, other.select(SqlRiskIntegrationTest.query("SELECT 1 AS id", Map.of())).size());
            }
            assertEquals(1, context.getBean(SqlRiskMonitor.class).recent(10).size());
        });
    }

    @Test void observeBindsAndDoesNotBlock() {
        runner.withPropertyValues("yulinlin.monitor.sql-risk.mode=observe")
                .withUserConfiguration(Custom.class).run(context -> {
                    var session = session(context.getSourceApplicationContext(), "jdbc:mysql:");
                    assertEquals(1, session.select(SqlRiskIntegrationTest.query("SELECT 1", Map.of())).size());
                    assertFalse(context.getBean(SqlRiskMonitor.class).recent(1).getFirst().blocked());
                });
    }

    @Test void offKeepsSlowMonitorButDisablesDetection() {
        runner.withPropertyValues("yulinlin.monitor.sql-risk.mode=off")
                .withUserConfiguration(Custom.class).run(context -> {
                    assertThat(context).hasSingleBean(DatabaseMonitor.class);
                    var session = session(context.getSourceApplicationContext(), "jdbc:mysql:");
                    assertEquals(1, session.select(SqlRiskIntegrationTest.query("SELECT 1", Map.of())).size());
                    assertTrue(context.getBean(SqlRiskMonitor.class).recent(1).isEmpty());
                });
    }

    @Test void disabledMonitorRegistersNoGuardEvenWithCustomDetector() {
        runner.withPropertyValues("yulinlin.monitor.enabled=false")
                .withUserConfiguration(Custom.class).run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcSqlGuard.class).doesNotHaveBean(DatabaseMonitor.class);
                    var session = session(context.getSourceApplicationContext(), "jdbc:mysql:");
                    assertEquals(1, session.select(SqlRiskIntegrationTest.query("SELECT 1", Map.of())).size());
                });
    }

    @Test void withoutMonitorAutoConfigurationJdbcKeepsItsOriginalBehavior() {
        base.run(context -> {
            assertThat(context).doesNotHaveBean(JdbcSqlGuard.class).doesNotHaveBean(SqlRiskDetector.class);
            var session = session(context.getSourceApplicationContext(), "jdbc:mysql:");
            assertEquals(1, session.select(SqlRiskIntegrationTest.query("SELECT 1", Map.of())).size());
        });
    }

    @Test void invalidModeFailsStartupRatherThanSilentlyDisablingProtection() {
        runner.withPropertyValues("yulinlin.monitor.sql-risk.mode=typo").run(context -> assertThat(context).hasFailed());
    }

    private JdbcSession session(org.springframework.context.ConfigurableApplicationContext context, String dialect) {
        var factory = new JdbcSessionFactory(dialect, MysqlSession::new);
        context.getAutowireCapableBeanFactory().autowireBean(factory);
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:risk_config;MODE=MySQL");
        return factory.create(source, "test");
    }

    @Configuration(proxyBeanMethods = false)
    static class Custom {
        @Bean SqlRiskDetector businessDetector() { return sql -> SqlRiskResult.reject("业务规则"); }
    }
}
