package com.yulinlin.sync;

import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 模拟 JDBC 协议，验证每批数据与游标提交顺序；不启动数据库。 */
class JdbcFullSyncJobTest {
    @Test void commitsDataBeforeCheckpointAndPreservesNullColumns() throws Exception {
        Fixture f = new Fixture(false, false);
        try (SyncManager manager = new SyncManager(new DataSyncProperties(), new MemorySyncTaskStore(), task -> {});
             SyncManager.ManualGuard guard = manager.beginManual("postgresql")) {
            SyncResult result = f.job(guard).run(f.input, f.output);
            assertEquals("COMPLETED", result.status());
            assertEquals("id-2", result.lastPrimaryKey());
            assertEquals(2, result.completedRows());
            assertEquals(2, result.rowsThisRun());
            PreparedStatement insert = f.first("INSERT INTO \"public\".\"full_sync_fixture\"");
            PreparedStatement checkpoint = f.first("UPDATE \"yulinlin_sync_progress\" SET last_key=");
            verify(insert).setObject(2, null);
            verify(checkpoint).setObject(1, "id-2");
            verify(checkpoint).setObject(2, 2L);
            var order = inOrder(insert, checkpoint, f.output);
            order.verify(insert).executeBatch();
            order.verify(checkpoint).executeUpdate();
            order.verify(f.output).commit();
            verify(f.route, atLeastOnce()).invalidateMaintenanceCache(f.target, FullSyncTest.Video.class);
        }
    }

    @Test void failedBatchDoesNotAdvanceCheckpoint() throws Exception {
        Fixture f = new Fixture(true, false);
        try (SyncManager manager = new SyncManager(new DataSyncProperties(), new MemorySyncTaskStore(), task -> {});
             SyncManager.ManualGuard guard = manager.beginManual("postgresql")) {
            assertThrows(SQLException.class, () -> f.job(guard).run(f.input, f.output));
            assertTrue(f.statements.keySet().stream().noneMatch(sql -> sql.contains("SET last_key=")));
            PreparedStatement status = f.first("UPDATE \"yulinlin_sync_progress\" SET status=?");
            verify(status).setObject(1, "FAILED");
            var order = inOrder(f.output, status);
            order.verify(f.output).rollback();
            order.verify(status).executeUpdate();
            order.verify(f.output).commit();
        }
    }

    @Test void resumesUsingSavedPrimaryKeyAndCumulativeCount() throws Exception {
        Fixture f = new Fixture(false, true);
        try (SyncManager manager = new SyncManager(new DataSyncProperties(), new MemorySyncTaskStore(), task -> {});
             SyncManager.ManualGuard guard = manager.beginManual("postgresql")) {
            SyncResult result = f.job(guard).run(f.input, f.output);
            assertEquals(9, result.completedRows());
            assertEquals(2, result.rowsThisRun());
            verify(f.sourceReads.getFirst()).setObject(1, "id-0");
            assertEquals("existing-task", result.taskId());
        }
    }

    private static final class Fixture {
        final Connection input = mock(Connection.class), output = mock(Connection.class);
        final JdbcSession source = mock(JdbcSession.class), target = mock(JdbcSession.class);
        final RouteSession route = mock(RouteSession.class);
        final Map<String, List<PreparedStatement>> statements = new LinkedHashMap<>();
        final List<PreparedStatement> sourceReads = new ArrayList<>();
        String signature;

        Fixture(boolean failBatch, boolean resume) throws Exception {
            metadata(input, "jdbc:postgresql:source");
            metadata(output, "jdbc:postgresql:target");
            when(source.group()).thenReturn("mysql");
            when(target.group()).thenReturn("postgresql");
            for (JdbcSession session : List.of(source, target)) {
                when(session.getCoderManager()).thenReturn(new JdbcCoderManager());
                when(session.maintenanceColumnName(any())).thenAnswer(call -> ((java.lang.reflect.Field) call.getArgument(0)).getName());
                doAnswer(call -> {
                    ((PreparedStatement) call.getArgument(0)).setObject(call.getArgument(1), call.getArgument(2));
                    return null;
                }).when(session).bindMaintenanceValue(any(), anyInt(), any());
            }
            when(source.readMaintenanceValue(any(), anyString(), anyInt()))
                    .thenAnswer(call -> ((ResultSet) call.getArgument(0)).getString((String) call.getArgument(1)));
            when(output.createStatement()).thenReturn(mock(Statement.class));
            when(input.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0);
                PreparedStatement statement = mock(PreparedStatement.class);
                if (sql.contains("WHERE 1=0")) {
                    ResultSet rows = emptyMetadataRows();
                    when(statement.executeQuery()).thenReturn(rows);
                }
                else {
                    boolean first = sourceReads.isEmpty();
                    sourceReads.add(statement);
                    ResultSet rows = first ? dataRows() : mock(ResultSet.class);
                    when(statement.executeQuery()).thenReturn(rows);
                }
                return statement;
            });
            when(output.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0);
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.computeIfAbsent(sql, ignored -> new ArrayList<>()).add(statement);
                when(statement.executeUpdate()).thenReturn(1);
                if (sql.contains("advisory")) {
                    ResultSet rows = mock(ResultSet.class);
                    when(rows.next()).thenReturn(true, false);
                    when(rows.getBoolean(1)).thenReturn(true);
                    when(statement.executeQuery()).thenReturn(rows);
                } else if (sql.contains("WHERE 1=0")) {
                    ResultSet rows = emptyMetadataRows();
                    when(statement.executeQuery()).thenReturn(rows);
                } else if (sql.startsWith("SELECT task_id") && resume) {
                    ResultSet rows = mock(ResultSet.class);
                    when(rows.next()).thenReturn(true, false);
                    when(rows.getString(1)).thenReturn("existing-task");
                    when(rows.getString(2)).thenAnswer(ignored -> signature);
                    when(rows.getString(3)).thenReturn("id-0");
                    when(rows.getLong(4)).thenReturn(7L);
                    when(rows.getString(5)).thenReturn("FAILED");
                    when(statement.executeQuery()).thenReturn(rows);
                } else when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
                if (sql.startsWith("INSERT INTO \"public\".")) {
                    if (failBatch) when(statement.executeBatch()).thenThrow(new SQLException("模拟批次失败"));
                    else when(statement.executeBatch()).thenReturn(new int[]{1, 1});
                }
                return statement;
            });
            FullSyncMapping from = new FullSyncMapping(input, source, FullSyncTest.Video.class);
            FullSyncMapping to = new FullSyncMapping(output, target, FullSyncTest.Video.class);
            signature = JdbcFullSyncJob.hash("jdbc:postgresql:source\0db\0public\0full_sync_fixture\0"
                    + "jdbc:postgresql:target\0db\0public\0full_sync_fixture"
                    + from.primarySignature + "/" + to.primarySignature + from.columns + to.columns
                    + from.fields.stream().map(field -> field.getName() + ":" + field.getGenericType().getTypeName()).toList());
        }

        JdbcFullSyncJob job(SyncManager.ManualGuard guard) {
            return new JdbcFullSyncJob(route, source, target, FullSyncTest.Video.class, 1000, guard, false);
        }
        PreparedStatement first(String prefix) {
            return statements.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                    .findFirst().orElseThrow().getValue().getFirst();
        }
    }

    private static void metadata(Connection connection, String url) throws Exception {
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("db");
        when(connection.getSchema()).thenReturn("public");
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getURL()).thenReturn(url);
        when(metadata.getPrimaryKeys(any(), any(), any())).thenAnswer(call -> {
            ResultSet keys = mock(ResultSet.class);
            when(keys.next()).thenReturn(true, false);
            when(keys.getString("COLUMN_NAME")).thenReturn("id");
            return keys;
        });
    }
    private static ResultSet emptyMetadataRows() throws SQLException {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnType(anyInt())).thenReturn(Types.VARCHAR);
        when(metadata.getPrecision(anyInt())).thenReturn(32);
        return rows;
    }
    private static ResultSet dataRows() throws SQLException {
        ResultSet rows = emptyMetadataRows();
        AtomicInteger index = new AtomicInteger();
        when(rows.next()).thenAnswer(call -> index.incrementAndGet() <= 2);
        when(rows.getString("c0")).thenAnswer(call -> "id-" + index.get());
        when(rows.getString("c1")).thenAnswer(call -> index.get() == 1 ? null : "标题");
        return rows;
    }
}
