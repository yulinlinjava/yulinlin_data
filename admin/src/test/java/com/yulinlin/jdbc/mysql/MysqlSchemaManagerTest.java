package com.yulinlin.jdbc.mysql;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.TextTypeEnum;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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

class MysqlSchemaManagerTest {
    @Test void sessionExposesTheSameInitialDdlWithoutOpeningAConnection() {
        DataSource dataSource = mock(DataSource.class);
        var session = new MysqlSession(dataSource);
        session.setProperties(new MysqlProperties());

        assertThat(session.createTableSql(PreviewUser.class))
                .anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"))
                .anyMatch(sql -> sql.contains("COMMENT 'Display name'"))
                .anyMatch(sql -> sql.startsWith("CREATE INDEX"));
        verifyNoInteractions(dataSource);
    }

    @Test void stringPrimaryKeyIsLimitedTo128CharactersAndCannotBeLargeText() {
        Connection connection = mock(Connection.class);
        var manager = new MysqlSchemaManager();

        assertThatThrownBy(() -> manager.ensureTable(connection, TooLongPrimary.class, true,
                SchemaMode.CREATE, sql -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 128");
        assertThatThrownBy(() -> manager.ensureTable(connection, LargeTextPrimary.class, true,
                SchemaMode.CREATE, sql -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be a primary key");
        verifyNoInteractions(connection);
    }

    @Test void createModeAddsMissingOrdinaryColumn() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        AtomicBoolean added = new AtomicBoolean();
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("catalog");
        when(metadata.getTables(eq("catalog"), isNull(), eq("%"), any(String[].class)))
                .thenAnswer(ignored -> rows(List.of(Map.of(
                        "TABLE_CAT", "catalog", "TABLE_SCHEM", "schema", "TABLE_NAME", "incremental_user"))));
        when(metadata.getPrimaryKeys(nullable(String.class), nullable(String.class), eq("incremental_user")))
                .thenAnswer(ignored -> rows(List.of(Map.of("COLUMN_NAME", "id"))));
        when(metadata.getColumns(nullable(String.class), nullable(String.class), eq("incremental_user"), eq("%")))
                .thenAnswer(ignored -> rows(added.get() ? List.of(
                        column("id", Types.VARCHAR, "VARCHAR", 128),
                        column("nickname", Types.VARCHAR, "VARCHAR", 64))
                        : List.of(column("id", Types.VARCHAR, "VARCHAR", 128))));

        List<String> executed = new ArrayList<>();
        assertThat(new MysqlSchemaManager().ensureTable(connection, IncrementalUser.class, true,
                SchemaMode.CREATE, sql -> {
                    executed.add(sql);
                    added.set(true);
                })).isTrue();

        assertThat(executed).containsExactly(
                "ALTER TABLE `incremental_user` ADD COLUMN `nickname` VARCHAR(64) COMMENT 'Nickname'");
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

    @JoinTable(value = "too_long_primary", autoSchema = true)
    static class TooLongPrimary {
        @JoinMeta(primaryKey = true)
        @JoinField(textLength = 129)
        String id;
    }

    @JoinTable(value = "large_text_primary", autoSchema = true)
    static class LargeTextPrimary {
        @JoinMeta(primaryKey = true)
        @JoinField(textType = TextTypeEnum.text)
        String id;
    }

    @JoinTable(value = "preview_user", autoSchema = true)
    @JoinIndex(fields = "name")
    static class PreviewUser {
        @JoinMeta(primaryKey = true)
        String id;
        @JoinField(description = "Display name")
        String name;
    }

    @JoinTable(value = "incremental_user", autoSchema = true)
    static class IncrementalUser {
        @JoinMeta(primaryKey = true) String id;
        @JoinField(textLength = 64, description = "Nickname") String nickname;
    }
}
