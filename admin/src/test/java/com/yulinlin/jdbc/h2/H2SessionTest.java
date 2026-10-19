package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.TextTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class H2SessionTest {
    @TempDir Path directory;

    @Test void bindsModuleOwnedCommonSettings() {
        var source = new MapConfigurationPropertySource(Map.of(
                "yulinlin.h2.log", true,
                "yulinlin.h2.map-underscore-to-camel-case", false,
                "yulinlin.h2.parallel-connections", 2,
                "yulinlin.h2.execute-batch-size", 64));
        var properties = new Binder(source).bind("yulinlin.h2", Bindable.of(H2Properties.class))
                .orElseThrow(() -> new AssertionError("H2 properties were not bound"));
        assertThat(properties.isLog()).isTrue();
        assertThat(properties.isMapUnderscoreToCamelCase()).isFalse();
        assertThat(properties.getParallelConnections()).isEqualTo(2);
        assertThat(properties.getExecuteBatchSize()).isEqualTo(64);
    }

    @Test void startupInitializationCreatesTableAndDeclaredIndex() {
        var properties = new H2Properties();
        properties.setFile(directory.resolve("startup").toString());
        try (var database = new H2Database(properties)) {
            var session = new H2Session(database.dataSource());
            session.setProperties(properties);
            session.configure(properties, database.schemaDataSource());
            assertThat(session.createTableSql(User.class))
                    .anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"))
                    .anyMatch(sql -> sql.contains("COMMENT ON COLUMN") && sql.contains("User''s display name"))
                    .anyMatch(sql -> sql.contains("CREATE INDEX IF NOT EXISTS"));
            session.initializeSchema(List.of(User.class));

            var sql = new JdbcTemplate(database.dataSource());
            assertThat(sql.queryForObject(
                    "select count(*) from information_schema.tables where table_name='startup_user'",
                    Integer.class)).isEqualTo(1);
            assertThat(sql.queryForObject(
                    "select count(*) from information_schema.indexes where index_name='idx_startup_user_name'",
                    Integer.class)).isEqualTo(1);
            assertThat(sql.queryForObject(
                    "select character_maximum_length from information_schema.columns "
                            + "where table_name='startup_user' and column_name='name'", Long.class)).isEqualTo(80L);
            assertThat(sql.queryForObject(
                    "select character_maximum_length from information_schema.columns "
                            + "where table_name='startup_user' and column_name='id'", Long.class)).isEqualTo(128L);
            assertThat(sql.queryForObject(
                    "select remarks from information_schema.columns "
                            + "where table_name='startup_user' and column_name='name'", String.class))
                    .isEqualTo("User's display name");
            assertThat(sql.queryForObject(
                    "select data_type from information_schema.columns "
                            + "where table_name='startup_user' and column_name='content'", String.class))
                    .isEqualToIgnoringCase("CHARACTER LARGE OBJECT");
        }
    }

    @Test void stringPrimaryKeyIsLimitedTo128CharactersAndCannotBeLargeText() {
        var properties = new H2Properties();
        properties.setFile(directory.resolve("invalid-primary").toString());
        try (var database = new H2Database(properties)) {
            var session = new H2Session(database.dataSource());
            session.setProperties(properties);
            session.configure(properties, database.schemaDataSource());

            assertThatThrownBy(() -> session.initializeSchema(List.of(TooLongPrimary.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot exceed 128");
            assertThatThrownBy(() -> session.initializeSchema(List.of(LargeTextPrimary.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be a primary key");
        }
    }

    @Test void noneModeDoesNotBorrowSchemaConnection() throws Exception {
        DataSource business = mock(DataSource.class);
        DataSource schema = mock(DataSource.class);
        var properties = new H2Properties();
        properties.setSchemaMode(SchemaMode.NONE);
        var session = new H2Session(business);
        session.setProperties(properties);
        session.configure(properties, schema);
        session.initializeSchema(List.of(User.class));
        verify(schema, never()).getConnection();
    }

    @Test void createModeAddsMissingOrdinaryColumnButValidateModeDoesNot() {
        var properties = new H2Properties();
        properties.setFile(directory.resolve("incremental").toString());
        try (var database = new H2Database(properties)) {
            var sql = new JdbcTemplate(database.dataSource());
            sql.execute("create table \"incremental_user\" (\"id\" varchar(128) not null primary key)");

            var session = new H2Session(database.dataSource());
            session.setProperties(properties);
            session.configure(properties, database.schemaDataSource());
            session.initializeSchema(List.of(IncrementalUser.class));

            assertThat(sql.queryForObject(
                    "select character_maximum_length from information_schema.columns "
                            + "where table_name='incremental_user' and column_name='nickname'", Long.class))
                    .isEqualTo(64L);
            assertThat(sql.queryForObject(
                    "select remarks from information_schema.columns "
                            + "where table_name='incremental_user' and column_name='nickname'", String.class))
                    .isEqualTo("Nickname");
        }

        properties.setFile(directory.resolve("validate-only").toString());
        properties.setSchemaMode(SchemaMode.VALIDATE);
        try (var database = new H2Database(properties)) {
            var sql = new JdbcTemplate(database.dataSource());
            sql.execute("create table \"incremental_user\" (\"id\" varchar(128) not null primary key)");
            var session = new H2Session(database.dataSource());
            session.setProperties(properties);
            session.configure(properties, database.schemaDataSource());

            assertThatThrownBy(() -> session.initializeSchema(List.of(IncrementalUser.class)))
                    .hasMessageContaining("nickname");
            assertThat(sql.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_name='incremental_user' and column_name='nickname'", Integer.class))
                    .isZero();
        }
    }

    @JoinTable(value = "startup_user", autoSchema = true)
    @JoinIndex(fields = "name")
    public static class User {
        @JoinMeta(primaryKey = true) private String id;
        @JoinField(textLength = 80, description = "User's display name") private String name;
        @JoinField(textType = TextTypeEnum.text, description = "Full content") private String content;
    }

    @JoinTable(value = "too_long_primary", autoSchema = true)
    public static class TooLongPrimary {
        @JoinMeta(primaryKey = true)
        @JoinField(textLength = 129)
        private String id;
    }

    @JoinTable(value = "large_text_primary", autoSchema = true)
    public static class LargeTextPrimary {
        @JoinMeta(primaryKey = true)
        @JoinField(textType = TextTypeEnum.text)
        private String id;
    }

    @JoinTable(value = "incremental_user", autoSchema = true)
    public static class IncrementalUser {
        @JoinMeta(primaryKey = true) private String id;
        @JoinField(textLength = 64, description = "Nickname") private String nickname;
    }
}
