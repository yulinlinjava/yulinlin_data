package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresqlSchemaManagerTest {
    @Test void sessionPreviewsSchemaQualifiedTableCommentsAndIndexesWithoutAConnection() {
        var session = new PostgresqlSession(null);
        session.setProperties(new PostgresqlProperties());

        assertThat(session.createTableSql(PreviewUser.class))
                .anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS \"app\".\"preview_user\""))
                .anyMatch(sql -> sql.equals(
                        "COMMENT ON COLUMN \"app\".\"preview_user\".\"name\" IS 'Display name'"))
                .anyMatch(sql -> sql.startsWith(
                        "CREATE INDEX IF NOT EXISTS \"idx_preview_user_name\" ON \"app\".\"preview_user\""))
                .anyMatch(sql -> sql.contains("USING GIN (to_tsvector('jiebacfg'::regconfig")
                        && sql.contains("COALESCE(\"name\", '')"));
    }

    @Test void createModeAddsMissingOrdinaryColumnAndValidateModeDoesNot() throws Exception {
        AtomicBoolean added = new AtomicBoolean();
        Connection connection = connection(added);
        List<String> executed = new ArrayList<>();
        var manager = new PostgresqlSchemaManager();

        assertThat(manager.ensureTable(connection, IncrementalUser.class, true,
                SchemaMode.CREATE, sql -> {
                    executed.add(sql);
                    added.set(true);
                })).isTrue();
        assertThat(executed).containsExactly(
                "ALTER TABLE \"incremental_user\" ADD COLUMN IF NOT EXISTS \"nickname\" VARCHAR(64)",
                "COMMENT ON COLUMN \"incremental_user\".\"nickname\" IS 'Nickname'");

        added.set(false);
        assertThatThrownBy(() -> manager.ensureTable(connection, IncrementalUser.class, true,
                SchemaMode.VALIDATE, sql -> { throw new AssertionError("unexpected DDL"); }))
                .hasMessageContaining("nickname");
    }

    @Test void fullTextSchemaFailsClearlyWhenPgJiebaConfigurationIsMissing() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT to_regconfig(?)")).thenReturn(statement);
        when(statement.executeQuery()).thenAnswer(ignored -> rows(List.of()));

        assertThatThrownBy(() -> new PostgresqlSchemaManager().ensureTable(connection, PreviewUser.class, true,
                SchemaMode.CREATE, sql -> { throw new AssertionError("unexpected DDL"); }))
                .hasMessageContaining("pg_jieba")
                .hasMessageContaining("jiebacfg");
        verify(statement).setString(1, "jiebacfg");
    }

    private static Connection connection(AtomicBoolean added) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("database");
        when(connection.getSchema()).thenReturn("public");
        when(metadata.getTables(eq("database"), eq("public"), eq("%"), isNull()))
                .thenAnswer(ignored -> rows(List.of(Map.of(
                        "TABLE_CAT", "database", "TABLE_SCHEM", "public",
                        "TABLE_NAME", "incremental_user", "TABLE_TYPE", "TABLE"))));
        when(metadata.getPrimaryKeys(eq("database"), eq("public"), eq("incremental_user")))
                .thenAnswer(ignored -> rows(List.of(Map.of("COLUMN_NAME", "id"))));
        when(metadata.getColumns(eq("database"), eq("public"), eq("incremental_user"), eq("%")))
                .thenAnswer(ignored -> rows(added.get() ? List.of(
                        column("id", Types.VARCHAR, "varchar", 128),
                        column("nickname", Types.VARCHAR, "varchar", 64))
                        : List.of(column("id", Types.VARCHAR, "varchar", 128))));
        return connection;
    }

    private static Map<String, Object> column(String name, int type, String typeName, int size) {
        return Map.of("COLUMN_NAME", name, "DATA_TYPE", type, "TYPE_NAME", typeName, "COLUMN_SIZE", size);
    }

    private static ResultSet rows(List<Map<String, Object>> values) throws Exception {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger cursor = new AtomicInteger(-1);
        when(result.next()).thenAnswer(ignored -> cursor.incrementAndGet() < values.size());
        when(result.getString(anyString())).thenAnswer(invocation -> {
            Object value = values.get(cursor.get()).get(invocation.getArgument(0, String.class));
            return value == null ? null : value.toString();
        });
        when(result.getInt(anyString())).thenAnswer(invocation -> {
            Object value = values.get(cursor.get()).get(invocation.getArgument(0, String.class));
            return value instanceof Number number ? number.intValue() : 0;
        });
        return result;
    }

    @JoinTable(value = "app.preview_user", autoSchema = true)
    @JoinIndex(fields = "name")
    static class PreviewUser {
        @JoinMeta(primaryKey = true) String id;
        @JoinField(description = "Display name", fullText = true) String name;
    }

    @JoinTable(value = "incremental_user", autoSchema = true)
    static class IncrementalUser {
        @JoinMeta(primaryKey = true) String id;
        @JoinField(textLength = 64, description = "Nickname") String nickname;
    }
}
