package com.yulinlin.admin.jdbc;

import com.yulinlin.jdbc.session.JdbcTransactionResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅使用模拟 JDBC 资源，不启动 Spring、不连接真实数据库。 */
@Timeout(10)
class JdbcTransactionResourcesTest {
    @Test
    void unusedTransactionDoesNotBorrow() {
        DataSource source = mock(DataSource.class);
        JdbcTransactionResources resources = new JdbcTransactionResources(source, 4);
        resources.finish(false);
        resources.finish(false);
        verifyNoInteractions(source);
        assertThrows(IllegalStateException.class, resources::initialize);
        assertThrows(IllegalStateException.class, resources::getConnection);
    }

    @Test
    void reusesConnectionAndRestoresAutoCommit() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = connection();
        when(source.getConnection()).thenReturn(connection);
        JdbcTransactionResources resources = new JdbcTransactionResources(source, 4).initialize();
        for (int i = 0; i < 3; i++) {
            assertSame(connection, resources.getConnection());
            resources.releaseConnection(connection);
        }
        assertThrows(IllegalStateException.class, () -> resources.releaseConnection(connection));
        resources.finish(false);
        verify(source, times(1)).getConnection();
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).close();
    }

    @Test
    void exhaustedExternalPoolDoesNotPreventReuseOrCommit() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection first = connection();
        Connection second = connection();
        Connection lateFirst = connection();
        Connection lateSecond = connection();
        CountDownLatch expansionStarted = new CountDownLatch(2);
        CountDownLatch allowLateConnections = new CountDownLatch(1);
        CountDownLatch lateClosed = new CountDownLatch(2);
        doAnswer(call -> { lateClosed.countDown(); return null; }).when(lateFirst).close();
        doAnswer(call -> { lateClosed.countDown(); return null; }).when(lateSecond).close();
        AtomicInteger calls = new AtomicInteger();
        when(source.getConnection()).thenAnswer(call -> {
            int index = calls.incrementAndGet();
            if (index == 1) return first;
            if (index == 2) return second;
            if (index > 4) throw new SQLException("非预期的扩容");
            expansionStarted.countDown();
            // 模拟池耗尽且驱动不响应中断，必须等已有事务归还连接。
            awaitUninterruptibly(allowLateConnections);
            return index == 3 ? lateFirst : lateSecond;
        });
        JdbcTransactionResources a = new JdbcTransactionResources(source, 4).initialize();
        JdbcTransactionResources b = new JdbcTransactionResources(source, 4).initialize();
        Connection heldA = a.getConnection();
        Connection heldB = b.getConnection();
        CompletableFuture<Connection> nextA = async(a::getConnection);
        CompletableFuture<Connection> nextB = async(b::getConnection);
        try {
            assertTrue(expansionStarted.await(2, TimeUnit.SECONDS));
            a.releaseConnection(heldA);
            b.releaseConnection(heldB);
            assertSame(first, nextA.get(2, TimeUnit.SECONDS));
            assertSame(second, nextB.get(2, TimeUnit.SECONDS));
            a.releaseConnection(first);
            b.releaseConnection(second);
            async(() -> { a.finish(false); return null; }).get(2, TimeUnit.SECONDS);
            async(() -> { b.finish(false); return null; }).get(2, TimeUnit.SECONDS);
            verify(first).commit();
            verify(second).commit();
            verify(first).close();
            verify(second).close();
            assertEquals(4, calls.get(), "每个事务最多保留一个未完成的扩容请求");
        } finally {
            allowLateConnections.countDown();
        }
        assertTrue(lateClosed.await(2, TimeUnit.SECONDS));
        verify(lateFirst, never()).commit();
        verify(lateSecond, never()).commit();
        verify(lateFirst).rollback();
        verify(lateSecond).rollback();
    }

    @Test
    void optionalExpansionFailureDoesNotRollbackExistingWork() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = connection();
        CountDownLatch expansionAttempted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(source.getConnection()).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) return connection;
            expansionAttempted.countDown();
            throw new SQLException("连接池获取连接超时");
        });
        JdbcTransactionResources resources = new JdbcTransactionResources(source, 4).initialize();
        Connection held = resources.getConnection();
        CompletableFuture<Connection> next = async(resources::getConnection);
        assertTrue(expansionAttempted.await(2, TimeUnit.SECONDS));
        resources.releaseConnection(held);
        assertSame(connection, next.get(2, TimeUnit.SECONDS));
        resources.releaseConnection(connection);
        resources.finish(false);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void finishingRejectsNewBorrowersButWaitsForCurrentBorrower() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = connection();
        when(source.getConnection()).thenReturn(connection);
        JdbcTransactionResources resources = new JdbcTransactionResources(source, 1).initialize();
        Connection held = resources.getConnection();
        CompletableFuture<Connection> waiting = async(resources::getConnection);
        CompletableFuture<Void> finishing = async(() -> { resources.finish(false); return null; });
        try {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> waiting.get(2, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertFalse(finishing.isDone(), "正在使用的连接不能被提前关闭");
            verify(connection, never()).commit();
            verify(connection, never()).close();
        } finally {
            resources.releaseConnection(held);
        }
        finishing.get(2, TimeUnit.SECONDS);
        verify(connection).commit();
        verify(connection).close();
    }

    @Test
    void commitFailureRollsBackRemainingConnectionsAndClosesAll() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection first = connection();
        Connection second = connection();
        when(source.getConnection()).thenReturn(first, second);
        JdbcTransactionResources resources = new JdbcTransactionResources(source, 2).initialize();
        Connection held = resources.getConnection();
        Connection expanded = async(resources::getConnection).get(2, TimeUnit.SECONDS);
        assertSame(second, expanded);
        resources.releaseConnection(held);
        resources.releaseConnection(expanded);
        SQLException commitFailure = new SQLException("提交失败");
        SQLException closeFailure = new SQLException("释放失败");
        doThrow(commitFailure).when(first).commit();
        doThrow(closeFailure).when(first).close();
        assertSame(commitFailure, assertThrows(SQLException.class, () -> resources.finish(false)));
        assertArrayEquals(new Throwable[]{closeFailure}, commitFailure.getSuppressed());
        verify(first).rollback();
        verify(second).rollback();
        verify(second, never()).commit();
        verify(first, never()).setAutoCommit(true);
        verify(second, never()).setAutoCommit(true);
        verify(first).close();
        verify(second).close();
        assertThrows(IllegalStateException.class, resources::getConnection);
    }

    @Test
    void firstConnectionFailureClosesItAndCannotBeRetriedInSameTransaction() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = connection();
        when(source.getConnection()).thenReturn(connection);
        SQLException failure = new SQLException("设置事务连接失败");
        doThrow(failure).when(connection).setAutoCommit(false);
        JdbcTransactionResources resources = new JdbcTransactionResources(source, 4);
        assertSame(failure, assertThrows(SQLException.class, resources::initialize));
        assertSame(failure, assertThrows(SQLException.class, resources::initialize));
        assertTrue(resources.isRollbackOnly());
        verify(source, times(1)).getConnection();
        verify(connection).close();
    }

    private static Connection connection() throws SQLException {
        Connection connection = mock(Connection.class);
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        when(connection.getAutoCommit()).thenAnswer(call -> autoCommit.get());
        doAnswer(call -> { autoCommit.set(call.getArgument(0)); return null; })
                .when(connection).setAutoCommit(anyBoolean());
        return connection;
    }

    private static <T> CompletableFuture<T> async(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try { result.complete(task.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        for (;;) {
            try { latch.await(); break; }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
