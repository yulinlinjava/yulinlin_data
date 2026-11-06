package com.yulinlin.jdbc;

import com.yulinlin.data.core.aop.JoinTransactionAop;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class OrmTransactionAutoConfigurationTest {
    @Test void bootKeepsTheOrmAdvisorWithoutInstallingANativeTransactionManager() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            assertTrue(context.getBeansOfType(PlatformTransactionManager.class).isEmpty());
            assertTrue(context.getBeansOfType(TransactionInterceptor.class).isEmpty());
            assertEquals(1, context.getBean(Service.class).depth());
            assertNull(TransactionContext.current());
        }
    }
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataJdbcApplication.class, AopAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class})
    static class Config {
        @Bean RouteSession route() { return RouteSession.builder().build(); }
        @Bean JoinTransactionAop ormAdvisor(ObjectProvider<RouteSession> route) {
            return new JoinTransactionAop(route::getObject);
        }
        @Bean Service service() { return new Service(); }
        @Bean DataSource dataSource() {
            return new AbstractDataSource() {
                @Override public Connection getConnection() throws SQLException { throw new SQLException("No SQL in this test"); }
                @Override public Connection getConnection(String user, String password) throws SQLException { return getConnection(); }
            };
        }
    }
    public static class Service {
        @Transactional public int depth() { return TransactionContext.current().depth(); }
    }
}
