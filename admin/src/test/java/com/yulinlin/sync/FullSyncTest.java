package com.yulinlin.sync;

import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.jdbc.session.JdbcSession;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只在 admin 测试源码中，无自动建表/业务扫描/真实数据库。 */
class FullSyncTest {
    @JoinTable("full_sync_fixture")
    static class Video {
        @JoinMeta(primaryKey = true) String id;
        String title;
    }

    @Test void cursorPreservesStringAndDoesNotRoundLargeIntegers() {
        assertEquals("00012", JdbcFullSyncJob.primary("00012", String.class));
        assertEquals(Long.MAX_VALUE, JdbcFullSyncJob.primary("9223372036854775807", Long.class));
        assertEquals(new BigInteger("999999999999999999999999"),
                JdbcFullSyncJob.primary("999999999999999999999999", BigInteger.class));
        assertThrows(NumberFormatException.class,
                () -> JdbcFullSyncJob.primary("9223372036854775808", Long.class));
        assertThrows(NumberFormatException.class, () -> JdbcFullSyncJob.primary("1.1", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> JdbcFullSyncJob.primary("1.1", Double.class));
    }

    @Test void manualOnlyConfigurationIsAllowedButBusinessTransactionIsRejectedBeforeRouting() {
        DataSyncProperties properties = new DataSyncProperties();
        try (SyncManager manager = new SyncManager(properties, new MemorySyncTaskStore(), task -> fail())) {
            DataSyncClient client = new DataSyncClient(() -> { fail("不能提前解析数据源"); return null; },
                    manager, properties);
            TransactionListenerManager transaction = new TransactionListenerManager();
            transaction.startTransaction();
            try {
                assertThrows(IllegalStateException.class,
                        () -> client.sync("mysql", "postgresql", Video.class));
            } finally { transaction.rollbackTransaction(); }
        }
    }

    @Test void maintenanceConnectionRollsBackRemainderAndRestoresAutoCommit() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        AtomicBoolean auto = new AtomicBoolean(true);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenAnswer(call -> auto.get());
        doAnswer(call -> { auto.set(call.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
        JdbcSession session = new JdbcSession(source);
        assertEquals("done", session.withMaintenanceConnection(c -> { c.setAutoCommit(false); return "done"; }));
        var order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
        order.verify(connection).close();
        verify(connection, never()).commit();
    }

    @Test void rollbackFailureAbortsInsteadOfSwitchingAutoCommitAndPreservesOriginalError() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("回滚失败")).when(connection).rollback();
        JdbcSession session = new JdbcSession(source);
        IllegalStateException original = new IllegalStateException("批次失败");
        Exception actual = assertThrows(IllegalStateException.class,
                () -> session.withMaintenanceConnection(c -> { throw original; }));
        assertSame(original, actual);
        assertEquals(1, actual.getSuppressed().length);
        verify(connection).abort(any());
        verify(connection, never()).setAutoCommit(anyBoolean());
        verify(connection).close();
    }
}
