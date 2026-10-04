package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.jdbc.sqlite.fixtures.SchemaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class SqliteSchemaManagerTest {
    @TempDir Path directory;
    private SqliteDataSource source() {
        var properties = new SqliteProperties();
        properties.setFile(directory.resolve("schema.db").toString());
        return new SqliteDataSource(properties);
    }

    @Test void scansCreatesAndRepeatsWithoutChangingData() {
        try (var source = source()) {
            var manager = new SqliteSchemaManager(source, true);
            var result = manager.scanAndCreate("com.yulinlin.jdbc.sqlite.fixtures");
            assertThat(result).isEqualTo(new SqliteSchemaManager.SchemaResult(1, 1, 0));
            var sql = new JdbcTemplate(source);
            var columns = sql.queryForList("pragma table_info(schema_entity)");
            assertThat(columns).extracting(c -> c.get("name")).contains("id", "display_name", "created_at")
                    .doesNotContain("ignored", "computed");
            for (String name : new String[]{"created_at", "state", "amount", "details", "payload", "bytes"}) {
                assertThat(columns.stream().filter(c -> name.equals(c.get("name"))).findFirst().orElseThrow().get("type")).isEqualTo("TEXT");
            }
            sql.update("insert into schema_entity(id,display_name) values('1','keep')");
            assertThat(manager.createTables(SchemaEntity.class).existing()).isEqualTo(1);
            assertThat(sql.queryForObject("select display_name from schema_entity", String.class)).isEqualTo("keep");
            sql.execute("alter table schema_entity add column extra text");
            assertThat(manager.createTables(SchemaEntity.class).existing()).isEqualTo(1);
        }
    }

    @Test void missingColumnRollsBackAllNewTables() {
        try (var source = source()) {
            var sql = new JdbcTemplate(source);
            sql.execute("create table schema_entity(id text primary key)");
            var manager = new SqliteSchemaManager(source, true);
            assertThatThrownBy(() -> manager.createTables(NewTable.class, SchemaEntity.class))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Incompatible SQLite column");
            assertThat(sql.queryForObject("select count(*) from sqlite_schema where name='a_new_table'", Integer.class)).isZero();
            assertThat(sql.queryForList("pragma table_info(schema_entity)")).hasSize(1);
        }
    }

    @Test void rejectsConflictingMappingsAndUnsafeNamesBeforeDdl() {
        try (var source = source()) {
            var manager = new SqliteSchemaManager(source, true);
            assertThatThrownBy(() -> manager.createTables(SchemaEntity.class, Conflict.class)).hasMessageContaining("Conflicting");
            assertThatThrownBy(() -> manager.createTables(Unsafe.class)).hasMessageContaining("plain");
            assertThatThrownBy(() -> manager.scanAndCreate()).hasMessageContaining("empty");
            assertThatThrownBy(() -> manager.scanAndCreate("no.such.entities")).hasMessageContaining("No SQLite");
        }
    }

    @Test void checksExistingTypeAndPrimaryKey() {
        try (var source = source()) {
            var manager = new SqliteSchemaManager(source, true);
            var sql = new JdbcTemplate(source);
            sql.execute("create table a_new_table(value integer)");
            assertThatThrownBy(() -> manager.createTables(NewTable.class)).hasMessageContaining("Incompatible SQLite column");
            sql.execute("drop table a_new_table");
            sql.execute("create table a_new_table(value text primary key)");
            assertThatThrownBy(() -> manager.createTables(NewTable.class)).hasMessageContaining("Incompatible SQLite column");
        }
    }

    @JoinTable("a_new_table") public static class NewTable { public String value; }
    @JoinTable("schema_entity") public static class Conflict { public Integer value; }
    @JoinTable("unsafe;drop") public static class Unsafe { public String value; }
}
