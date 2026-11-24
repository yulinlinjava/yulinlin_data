package com.yulinlin.data.core.aop;

import com.yulinlin.data.core.anno.JoinTransaction;
import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.transaction.TransactionContext;
import com.yulinlin.data.core.transaction.TransactionListener;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;

import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JoinTransactionAopTest {
    static final class Listener implements TransactionListener {
        int starts, commits, rollbacks, completed;
        @Override public void startTransaction() { starts++; }
        @Override public void commitTransaction() { commits++; }
        @Override public void rollbackTransaction() { rollbacks++; }
        @Override public void afterCompletion() { completed++; }
    }
    private <T> T proxy(T target, RouteSession route, boolean cglib) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(cglib);
        factory.addAdvisor(new JoinTransactionAop(() -> route));
        return (T) factory.getProxy();
    }

    @Test void bothAnnotationsUseOneScopeAndSpringExplicitRules() {
        Listener listener = new Listener();
        RouteSession route = RouteSession.builder().transactionListeners(List.of(listener)).build();
        Both service = proxy(new Both(), route, true);
        assertThrows(IOException.class, service::run);
        assertEquals(1, listener.starts);
        assertEquals(1, listener.commits);
        assertEquals(0, listener.rollbacks);
        assertEquals(1, listener.completed);
        assertNull(TransactionContext.current());
    }
    public static class Both {
        @JoinTransaction @Transactional(noRollbackFor = IOException.class)
        public void run() throws IOException {
            assertEquals(1, TransactionContext.current().depth());
            throw new IOException("explicit rule");
        }
    }

    @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.METHOD})
    @Transactional
    public @interface BusinessTransaction { }
    public interface Contract {
        @Transactional void run() throws IOException;
    }
    public static class Implementation implements Contract {
        public void run() throws IOException {
            assertNotNull(TransactionContext.current());
            throw new IOException("checked");
        }
    }
    @BusinessTransaction public static class ClassLevel {
        public void run() throws IOException { throw new IOException("class"); }
        @Transactional(noRollbackForClassName = "java.io.IOException")
        public void override() throws IOException { throw new IOException("method"); }
    }

    @Test void interfaceAndMetaClassAnnotationsAndMethodOverrideAreResolved() {
        Listener listener = new Listener();
        RouteSession route = RouteSession.builder().transactionListeners(List.of(listener)).build();
        Contract contract = proxy((Contract) new Implementation(), route, false);
        assertThrows(IOException.class, contract::run);
        ClassLevel classLevel = proxy(new ClassLevel(), route, true);
        assertThrows(IOException.class, classLevel::run);
        assertThrows(IOException.class, classLevel::override);
        assertEquals(3, listener.starts);
        assertEquals(2, listener.rollbacks);
        assertEquals(1, listener.commits);
    }

    /** 不生效的 Spring 选项：忽略并打日志，照常在 ORM 事务里执行。 */
    public static class Ignored {
        @Transactional(propagation = Propagation.REQUIRES_NEW) public int propagation() { return TransactionContext.current().depth(); }
        @Transactional(isolation = Isolation.SERIALIZABLE) public int isolation() { return TransactionContext.current().depth(); }
        @Transactional(transactionManager = "other") public int manager() { return TransactionContext.current().depth(); }
        @Transactional(label = "tag") public int label() { return TransactionContext.current().depth(); }
        @JoinTransaction({"mysql"}) public int groups() { return TransactionContext.current().depth(); }
    }
    @Test void ignoredSpringOptionsStillRunInOrmTransaction() {
        var advisor = new JoinTransactionAop();
        for (Method method : Ignored.class.getDeclaredMethods())
            assertTrue(advisor.matches(method, Ignored.class), method.getName());
        Ignored service = proxy(new Ignored(), RouteSession.builder().build(), true);
        assertEquals(1, service.propagation());
        assertEquals(1, service.isolation());
        assertEquals(1, service.manager());
        assertEquals(1, service.label());
        assertEquals(1, service.groups());
        assertNull(TransactionContext.current());
    }

    /** 写错的超时仍然在启动时报错。 */
    public static class Unsupported {
        @Transactional(timeoutString = "soon") public void badTimeoutString() { }
        @Transactional(timeout = 0) public void zeroTimeout() { }
        @JoinTransaction(timeout = 0) public void zeroJoinTimeout() { }
    }
    @Test void invalidTimeoutsAreRejectedBeforeBusinessInvocation() {
        var advisor = new JoinTransactionAop();
        for (Method method : Unsupported.class.getDeclaredMethods())
            assertThrows(OrmException.class, () -> advisor.matches(method, Unsupported.class), method.getName());
    }

    public static class Timed {
        @Transactional(timeout = 3) public long spring() { return TransactionContext.current().remainingMillis(); }
        @Transactional(timeoutString = "4") public long springString() { return TransactionContext.current().remainingMillis(); }
        @JoinTransaction(timeout = 5) public long join() { return TransactionContext.current().remainingMillis(); }
        @JoinTransaction(timeout = 5) @Transactional(timeout = 2) public long both() { return TransactionContext.current().remainingMillis(); }
        @Transactional(readOnly = true) public long readOnly() { return TransactionContext.current().remainingMillis(); }
    }
    @Test void timeoutsAreAppliedAndReadOnlyIsIgnored() {
        Timed service = proxy(new Timed(), RouteSession.builder().build(), true);
        assertTrue(service.spring() > 0 && service.spring() <= 3000);
        assertTrue(service.springString() > 3000 && service.springString() <= 4000);
        assertTrue(service.join() > 4000 && service.join() <= 5000);
        assertTrue(service.both() > 0 && service.both() <= 2000, "@Transactional 的超时优先");
        assertEquals(Long.MAX_VALUE, service.readOnly(), "readOnly 忽略，也没有超时");
        assertNull(TransactionContext.current());
    }

    @Configuration(proxyBeanMethods = false) @EnableAspectJAutoProxy
    static class InvalidOptionsConfig {
        @Bean JoinTransactionAop ormAdvisor() { return new JoinTransactionAop(); }
        @Bean Unsupported unsupported() { return new Unsupported(); }
    }
    @Test void unsupportedOptionsFailApplicationStartup() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(InvalidOptionsConfig.class);
            assertThrows(Exception.class, context::refresh);
            assertNull(TransactionContext.current());
        }
    }

    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement
    static class NativeConfig {
        @Bean JoinTransactionAop ormAdvisor() { return new JoinTransactionAop(); }
    }
    @Test void nativeSpringInterceptorConflictFailsApplicationStartup() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(NativeConfig.class);
            Exception failure = assertThrows(Exception.class, context::refresh);
            assertTrue(rootCause(failure).getMessage().contains("不能同时启用"));
        }
    }
    @Test void failingRollbackRuleAndUnpairedManualScopeDoNotLeakContext() {
        var route = RouteSession.builder().build();
        IOException original = new IOException("business");
        IOException failure = assertThrows(IOException.class, () -> route.transaction(
                () -> { throw original; },
                error -> { throw new IllegalStateException("broken rule"); }));
        assertSame(original, failure);
        assertEquals("broken rule", failure.getSuppressed()[0].getMessage());
        assertFalse(route.isOpenTransaction());
        assertThrows(OrmException.class, () -> route.transaction(() -> {
            route.startTransaction();
            return null;
        }));
        assertFalse(route.isOpenTransaction());
    }

    private static Throwable rootCause(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error;
    }
}
