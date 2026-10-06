package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Startup schema initialization cases; runtime requests never infer tables from fromClass. */
class SqliteSessionTest {
    @TempDir Path directory;
    private final List<SqliteSession> sessions = new ArrayList<>();

    @AfterEach void closeExecutors() {
        sessions.forEach(session -> session.getThreadPoolExecutor().shutdownNow());
    }

    private SqliteDataSource source(String file) {
        var properties = new SqliteProperties();
        properties.setFile(directory.resolve(file).toString());
        return new SqliteDataSource(properties);
    }

    private SqliteSession session(SqliteDataSource source) {
        var session = new SqliteSession(source);
        var properties = new JdbcProperties();
        properties.setMapUnderscoreToCamelCase(true);
        session.setProperties(properties);
        session.setCoderManager(new JdbcCoderManager());
        session.setCacheManager(new DbCache());
        session.setLogManager(new LogManager());
        session.setFilterManager(new IFilterManager() { });
        var proxy = mock(EntityProxyService.class);
        doAnswer(call -> call.getArgument(0)).when(proxy).getLazyProxyList(anyList());
        session.setProxyService(proxy);
        sessions.add(session);
        return session;
    }

    private static int tableCount(SqliteDataSource source, String table) {
        return new JdbcTemplate(source).queryForObject(
                "select count(*) from sqlite_schema where type='table' and name=?", Integer.class, table);
    }

    @Test void startupInitializationCreatesTableBeforeCrud() {
        try (var source = source("startup.db")) {
            var session = session(source);
            assertThat(tableCount(source, "local_user")).isZero();
            assertThat(session.createTableSql(User.class)).singleElement()
                    .asString().contains("CREATE TABLE IF NOT EXISTS", "VARCHAR(128)");
            session.initializeSchema(List.of(User.class));
            assertThat(tableCount(source, "local_user")).isEqualTo(1);

            var insert = ExecuteRequest.newInstance(
                    "insert into local_user(id,name) values(#{id},#{name})",
                    Map.of("id", "1", "name", "alice"));
            assertThat(session.update(insert)).isEqualTo(1);
            assertThat(session.select(QueryRequest.newInstance(
                    "select id,name from local_user", Map.of(), Map.class))).hasSize(1);
        }
    }

    @Test void runtimeFromClassNoLongerCreatesTables() {
        try (var source = source("no-lazy.db")) {
            var session = session(source);
            var request = QueryRequest.newInstance("select id from unscanned_table", Map.of(), Map.class);
            request.setFromClass(Unscanned.class);
            assertThatThrownBy(() -> session.select(request)).hasMessageContaining("no such table");
            assertThat(tableCount(source, "unscanned_table")).isZero();
        }
    }

    @Test void projectionMarkedAutoSchemaFalseIsIgnored() {
        try (var source = source("projection.db")) {
            session(source).initializeSchema(List.of(Projection.class));
            assertThat(tableCount(source, "local_user")).isZero();
        }
    }

    @Test void sqliteKeepsSingleWriterAndRollsBackBusinessRows() {
        try (var source = source("transaction.db")) {
            var session = session(source);
            session.initializeSchema(List.of(User.class));
            assertThat(session.supportsParallelWrites()).isFalse();
            assertThat(session.getParallelConnections()).isEqualTo(1);

            var insert = ExecuteRequest.newInstance(
                    "insert into local_user(id,name) values(#{id},#{name})",
                    Map.of("id", "1", "name", "rollback"));
            session.startTransaction();
            try {
                assertThat(session.update(insert)).isEqualTo(1);
                session.rollbackTransaction();
            } finally {
                while (session.isOpenTransaction()) session.rollbackTransaction();
            }
            assertThat(new JdbcTemplate(source).queryForObject(
                    "select count(*) from local_user", Integer.class)).isZero();
        }
    }

    @JoinTable(value = "local_user", autoSchema = true)
    public static class User {
        @JoinMeta(primaryKey = true) private String id;
        private String name;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JoinTable(value = "local_user", autoSchema = false)
    public static class Projection { private String id; }

    @JoinTable(value = "unscanned_table", autoSchema = true)
    public static class Unscanned { private String id; }
}
