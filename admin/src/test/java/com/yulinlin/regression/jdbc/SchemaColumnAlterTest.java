package com.yulinlin.regression.jdbc;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.h2.H2SchemaManager;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** H2 回归仅放测试源码；包名与 com.yulinlin.admin 业务扫描隔离，不启动 Spring、不连接业务数据库。 */
class SchemaColumnAlterTest {
    @JoinTable(value = "alter_example", autoSchema = true)
    static class Numbers {
        @JoinMeta(primaryKey = true) String id;
        Long amount;
    }

    @JoinTable(value = "alter_example", autoSchema = true)
    static class Texts {
        @JoinMeta(primaryKey = true) String id;
        @JoinField(textLength = 64) String amount;
    }

    @Test
    void defaultOffAndValidateModeNeverAlter() throws Exception {
        assertFalse(new JdbcSessionProperties().isAlterColumnType());
        try (Connection connection = database()) {
            legacy(connection, "VARCHAR(255)", "'7'", "7");
            List<String> sql = new ArrayList<>();
            H2SchemaManager manager = new H2SchemaManager();
            assertThrows(IllegalStateException.class, () -> manager.ensureTable(
                    connection, Numbers.class, true, SchemaMode.CREATE, sql::add));
            assertThrows(IllegalStateException.class, () -> manager.ensureTable(
                    connection, Numbers.class, true, SchemaMode.VALIDATE, true, sql::add));
            assertTrue(sql.isEmpty());
            assertFalse(manager.ensureTable(connection, Numbers.class, true, SchemaMode.NONE, true, sql::add));
        }
    }

    @Test
    void convertsTextToNumberAndPreservesDefaultNullabilityCommentAndIndex() throws Exception {
        try (Connection connection = database(); var statement = connection.createStatement()) {
            legacy(connection, "VARCHAR(255)", "'7'", "12");
            statement.execute("COMMENT ON COLUMN \"alter_example\".\"amount\" IS '业务计数'");
            statement.execute("CREATE INDEX \"extra_amount_idx\" ON \"alter_example\"(\"amount\")");
            List<String> sql = new ArrayList<>();
            H2SchemaManager manager = new H2SchemaManager();
            assertTrue(manager.ensureTable(connection, Numbers.class, true, SchemaMode.CREATE, true, ddl -> {
                sql.add(ddl);
                statement.executeUpdate(ddl);
            }));
            assertEquals(1, sql.size());
            assertTrue(sql.getFirst().contains("SET DATA TYPE BIGINT"));
            try (var rows = statement.executeQuery("SELECT \"amount\" FROM \"alter_example\" WHERE \"id\"='a'")) {
                assertTrue(rows.next());
                assertEquals(12L, rows.getLong(1));
            }
            statement.execute("INSERT INTO \"alter_example\"(\"id\") VALUES ('b')");
            try (var rows = statement.executeQuery("SELECT \"amount\" FROM \"alter_example\" WHERE \"id\"='b'")) {
                assertTrue(rows.next());
                assertEquals(7L, rows.getLong(1));
            }
            try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), "PUBLIC", "alter_example", "amount")) {
                assertTrue(rows.next());
                assertEquals(java.sql.Types.BIGINT, rows.getInt("DATA_TYPE"));
                assertEquals(java.sql.DatabaseMetaData.columnNoNulls, rows.getInt("NULLABLE"));
                assertEquals("业务计数", rows.getString("REMARKS"));
            }
            boolean found = false;
            try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), "PUBLIC", "alter_example", false, false)) {
                while (rows.next()) if ("extra_amount_idx".equals(rows.getString("INDEX_NAME"))) found = true;
            }
            assertTrue(found);
            sql.clear();
            manager.ensureTable(connection, Numbers.class, true, SchemaMode.CREATE, true, sql::add);
            assertTrue(sql.isEmpty(), "第二次启动不重复改列");
        }
    }

    @Test
    void expandsExplicitLengthButDoesNotShrinkWiderColumns() throws Exception {
        try (Connection connection = database(); var statement = connection.createStatement()) {
            legacy(connection, "VARCHAR(32)", "'x'", "x");
            List<String> sql = new ArrayList<>();
            new H2SchemaManager().ensureTable(connection, Texts.class, true, SchemaMode.CREATE, true, ddl -> {
                sql.add(ddl);
                statement.executeUpdate(ddl);
            });
            assertEquals(1, sql.size());
            assertTrue(sql.getFirst().contains("CHARACTER VARYING(64)"));
            statement.execute("ALTER TABLE \"alter_example\" ALTER COLUMN \"amount\" SET DATA TYPE VARCHAR(128)");
            sql.clear();
            new H2SchemaManager().ensureTable(connection, Texts.class, true, SchemaMode.CREATE, true, sql::add);
            assertTrue(sql.isEmpty());
        }
    }

    @Test
    void incompatibleDataStopsInitializationWithDdlInCause() throws Exception {
        try (Connection connection = database(); var statement = connection.createStatement()) {
            legacy(connection, "VARCHAR(255)", "'7'", "not-a-number");
            var error = assertThrows(IllegalStateException.class, () -> new H2SchemaManager().ensureTable(
                    connection, Numbers.class, true, SchemaMode.CREATE, true, statement::executeUpdate));
            assertNotNull(error.getCause());
            assertTrue(error.getCause().getMessage().contains("自动修改列失败"));
            assertTrue(error.getCause().getMessage().contains("SET DATA TYPE BIGINT"));
        }
    }

    @Test
    void primaryKeyMismatchCannotBeFixedByTypeSwitch() throws Exception {
        try (Connection connection = database(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE \"alter_example\"(\"id\" VARCHAR(32), \"amount\" VARCHAR(255))");
            List<String> sql = new ArrayList<>();
            assertThrows(IllegalStateException.class, () -> new H2SchemaManager().ensureTable(
                    connection, Numbers.class, true, SchemaMode.CREATE, true, sql::add));
            assertTrue(sql.isEmpty());
        }
    }

    private static Connection database() throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:alter_" + UUID.randomUUID());
    }

    private static void legacy(Connection connection, String type, String defaultValue, String value) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE \"alter_example\"(\"id\" VARCHAR(32) PRIMARY KEY, "
                    + "\"amount\" " + type + " NOT NULL DEFAULT " + defaultValue + ")");
            statement.execute("INSERT INTO \"alter_example\" VALUES ('a', '" + value + "')");
        }
    }
}
