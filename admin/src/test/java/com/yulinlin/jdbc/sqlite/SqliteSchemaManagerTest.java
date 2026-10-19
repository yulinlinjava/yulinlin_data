package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.anno.JoinAggregations;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.TextTypeEnum;
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.sqlite.fixtures.QueryOnly;
import com.yulinlin.jdbc.sqlite.fixtures.SchemaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqliteSchemaManagerTest {
    @TempDir Path directory;

    private SqliteDataSource source() {
        var properties = new SqliteProperties();
        properties.setFile(directory.resolve("schema.db").toString());
        return new SqliteDataSource(properties);
    }

    private boolean ensure(SqliteDataSource source, SqliteSchemaManager manager, Class<?> entity) throws Exception {
        try (var connection = source.getConnection()) {
            return ensure(connection, manager, entity);
        }
    }

    private boolean ensure(java.sql.Connection connection, SqliteSchemaManager manager,
                           Class<?> entity) throws Exception {
        try (var statement = connection.createStatement()) {
            return manager.ensureTable(connection, entity, true, SchemaMode.CREATE, statement::executeUpdate);
        }
    }

    private boolean ensure(java.sql.Connection connection, SqliteSchemaManager manager,
                           Class<?> entity, SchemaMode mode) throws Exception {
        try (var statement = connection.createStatement()) {
            return manager.ensureTable(connection, entity, true, mode, statement::executeUpdate);
        }
    }

    @Test void createsFromEntityAndRepeatsWithoutChangingData() throws Exception {
        try (var source = source()) {
            var manager = new SqliteSchemaManager();
            assertThat(ensure(source, manager, SchemaEntity.class)).isTrue();
            var sql = new JdbcTemplate(source);
            var columns = sql.queryForList("pragma table_info(schema_entity)");
            assertThat(columns).extracting(c -> c.get("name")).contains("id", "display_name", "created_at")
                    .doesNotContain("ignored", "computed");
            assertThat(columns.stream().filter(c -> "id".equals(c.get("name"))).findFirst().orElseThrow().get("type"))
                    .isEqualTo("VARCHAR(128)");
            assertThat(columns.stream().filter(c -> "display_name".equals(c.get("name"))).findFirst().orElseThrow().get("type"))
                    .isEqualTo("VARCHAR(80)");
            for (String name : new String[]{"created_at", "state", "amount", "details", "payload", "bytes"}) {
                assertThat(columns.stream().filter(c -> name.equals(c.get("name"))).findFirst().orElseThrow().get("type"))
                        .isEqualTo("TEXT");
            }
            sql.update("insert into schema_entity(id,display_name) values('1','keep')");
            assertThat(ensure(source, manager, SchemaEntity.class)).isTrue();
            assertThat(sql.queryForObject("select display_name from schema_entity", String.class)).isEqualTo("keep");
            assertThat(ensure(source, manager, ExtendedSchemaEntity.class)).isTrue();
            assertThat(sql.queryForList("pragma table_info(schema_entity)"))
                    .extracting(column -> column.get("name")).contains("new_value");
            assertThat(sql.queryForObject("select new_value from schema_entity where id='1'", String.class)).isNull();
            sql.execute("alter table schema_entity add column extra text");
            assertThat(ensure(source, manager, SchemaEntity.class)).isTrue();
        }
    }

    @Test void callerControlsRollbackOfNewTables() throws Exception {
        try (var source = source()) {
            var sql = new JdbcTemplate(source);
            sql.execute("create table schema_entity(id text primary key, display_name integer)");
            var manager = new SqliteSchemaManager();
            try (var connection = source.getConnection()) {
                connection.setAutoCommit(false);
                ensure(connection, manager, NewTable.class);
                assertThatThrownBy(() -> ensure(connection, manager, SchemaEntity.class))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Incompatible SQLite column");
                connection.rollback();
            }
            assertThat(sql.queryForObject("select count(*) from sqlite_schema where name='a_new_table'", Integer.class)).isZero();
            assertThat(sql.queryForList("pragma table_info(schema_entity)")).hasSize(2);
        }
    }

    @Test void rejectsConflictingModelsAndUnsafeNamesWithoutMigrating() throws Exception {
        try (var source = source()) {
            var manager = new SqliteSchemaManager();
            ensure(source, manager, SchemaEntity.class);
            assertThatThrownBy(() -> ensure(source, manager, Conflict.class)).hasMessageContaining("Incompatible");
            assertThatThrownBy(() -> ensure(source, manager, Unsafe.class)).hasMessageContaining("plain");
            assertThat(new JdbcTemplate(source).queryForList("pragma table_info(schema_entity)"))
                    .noneMatch(column -> "value".equals(column.get("name")));
        }
    }

    @Test void checksExistingTypeAndPrimaryKey() {
        try (var source = source()) {
            var manager = new SqliteSchemaManager();
            var sql = new JdbcTemplate(source);
            sql.execute("create table a_new_table(value integer)");
            assertThatThrownBy(() -> ensure(source, manager, NewTable.class)).hasMessageContaining("Incompatible SQLite column");
            sql.execute("drop table a_new_table");
            sql.execute("create table a_new_table(value text primary key)");
            assertThatThrownBy(() -> ensure(source, manager, NewTable.class)).hasMessageContaining("Incompatible SQLite column");
        }
    }

    @Test void validateModeNeverCreatesMissingTablesOrColumns() throws Exception {
        try (var source = source(); var connection = source.getConnection()) {
            var manager = new SqliteSchemaManager();
            assertThatThrownBy(() -> ensure(connection, manager, SchemaEntity.class, SchemaMode.VALIDATE))
                    .hasMessageContaining("SQLite table is missing");

            ensure(connection, manager, SchemaEntity.class, SchemaMode.CREATE);
            assertThatThrownBy(() -> ensure(connection, manager, ExtendedSchemaEntity.class, SchemaMode.VALIDATE))
                    .hasMessageContaining("new_value");
            try (var statement = connection.createStatement();
                 var columns = statement.executeQuery("pragma table_info(schema_entity)")) {
                while (columns.next()) {
                    assertThat(columns.getString("name")).isNotEqualTo("new_value");
                }
            }
        }
    }

    @Test void skipsUnmappedJoinAndStatisticsModelsWithoutTouchingConnection() {
        var manager = new SqliteSchemaManager();
        var connection = mock(java.sql.Connection.class);
        for (Class<?> entity : new Class<?>[]{null, Object.class, java.util.Map.class, QueryOnly.class, Statistics.class}) {
            assertThat(manager.ensureTable(connection, entity, true, SchemaMode.CREATE,
                    sql -> fail("unexpected DDL"))).isFalse();
        }
        verifyNoInteractions(connection);
    }

    @Test void associationFieldsAreNotPhysicalColumns() throws Exception {
        try (var source = source()) {
            ensure(source, new SqliteSchemaManager(), Relations.class);
            assertThat(new JdbcTemplate(source).queryForList("pragma table_info(relations)"))
                    .extracting(column -> column.get("name")).containsExactly("name");
        }
    }

    @Test void doesNotCompleteOrCloseCallersConnection() throws Exception {
        try (var source = source(); var connection = source.getConnection()) {
            var observed = spy(connection);
            assertThat(ensure(observed, new SqliteSchemaManager(), NewTable.class)).isTrue();
            verify(observed, never()).setAutoCommit(anyBoolean());
            verify(observed, never()).commit();
            verify(observed, never()).rollback();
            verify(observed, never()).close();
        }
    }

    @Test void missingTableInReadOnlyTransactionFailsBeforeDdl() throws Exception {
        try (var source = source()) {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            try {
                assertThatThrownBy(() -> ensure(source, new SqliteSchemaManager(), NewTable.class))
                        .hasMessageContaining("outside a read-only transaction");
            } finally { TransactionSynchronizationManager.setCurrentTransactionReadOnly(false); }
            assertThat(new JdbcTemplate(source).queryForObject(
                    "select count(*) from sqlite_schema where name='a_new_table'", Integer.class)).isZero();
        }
    }

    @Test void stringPrimaryKeyIsLimitedTo128CharactersAndCannotBeLargeText() {
        try (var source = source()) {
            var manager = new SqliteSchemaManager();
            assertThatThrownBy(() -> ensure(source, manager, TooLongPrimary.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot exceed 128");
            assertThatThrownBy(() -> ensure(source, manager, LargeTextPrimary.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be a primary key");
        }
    }

    @JoinTable(value = "a_new_table", autoSchema = true) public static class NewTable { public String value; }
    @JoinTable(value = "schema_entity", autoSchema = true) public static class Conflict {
        @JoinField(name = "display_name") public Integer value;
    }
    @JoinTable(value = "schema_entity", autoSchema = true)
    public static class ExtendedSchemaEntity extends SchemaEntity { public String newValue; }
    @JoinTable(value = "unsafe;drop", autoSchema = true) public static class Unsafe { public String value; }
    @JoinTable(value = "statistics", autoSchema = true) public static class Statistics { @JoinAggregations public String name; }
    @JoinTable(value = "relations", autoSchema = true) public static class Relations {
        public String name;
        @JoinQuery public SchemaEntity child;
    }
    @JoinTable(value = "too_long_primary", autoSchema = true) public static class TooLongPrimary {
        @JoinMeta(primaryKey = true) @JoinField(textLength = 129) public String id;
    }
    @JoinTable(value = "large_text_primary", autoSchema = true) public static class LargeTextPrimary {
        @JoinMeta(primaryKey = true) @JoinField(textType = TextTypeEnum.text) public String id;
    }
}
