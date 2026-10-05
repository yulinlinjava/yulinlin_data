package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Regression sources for on-demand tables. These cases do not use the global router. */
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

    private QueryRequest<Map> select() {
        var request = QueryRequest.newInstance("select id,name from local_user", Map.of(), Map.class);
        request.setFromClass(User.class);
        return request;
    }

    private ExecuteRequest<Object> write(String sql, Map<String, Object> parameters) {
        var request = ExecuteRequest.<Object>newInstance(sql, parameters);
        request.setFromClass(User.class);
        return request;
    }

    private int tableCount(SqliteDataSource source) {
        return new JdbcTemplate(source).queryForObject(
                "select count(*) from sqlite_schema where name='local_user'", Integer.class);
    }

    @Test void firstQueryUsesFromClassEvenWhenResultTypeIsMap() {
        try (var source = source("query.db")) {
            var session = session(source);
            assertThat(tableCount(source)).isZero();
            assertThat(session.select(select())).isEmpty();
            assertThat(tableCount(source)).isEqualTo(1);
        }
    }

    @Test void rawSqlDoesNotInferTablesUnlessFromClassIsSet() {
        try (var source = source("raw.db")) {
            var session = session(source);
            var request = QueryRequest.newInstance("select id,name from local_user", Map.of(), Map.class);
            assertThatThrownBy(() -> session.select(request)).hasMessageContaining("no such table");
            assertThat(tableCount(source)).isZero();
            request.setFromClass(User.class);
            assertThat(session.select(request)).isEmpty();
            assertThat(session.select(QueryRequest.newInstance("select 1 as value", Map.of(), Map.class))).hasSize(1);
        }
    }

    @Test void updateAndDeleteAlsoPrepareTheirEntityTable() {
        for (String operation : List.of("update local_user set name='test'", "delete from local_user")) {
            try (var source = source(operation.startsWith("update") ? "update.db" : "delete.db")) {
                assertThat(session(source).update(write(operation, Map.of()))).isZero();
                assertThat(tableCount(source)).isEqualTo(1);
            }
        }
    }

    @Test void customQueryAndWriteWithNoSourceExecuteWithoutCreatingEntityTables() {
        for (Class<?> fromClass : new Class<?>[]{null, Object.class}) {
            try (var source = source(fromClass == null ? "null-source.db" : "object-source.db")) {
                var session = session(source);
                var sql = new JdbcTemplate(source);
                sql.execute("create table manual_entries(id text primary key, name text)");
                var insert = ExecuteRequest.newInstance(
                        "insert into manual_entries(id,name) values(#{id},#{name})",
                        Map.of("id", "1", "name", "alice"));
                insert.setFromClass(fromClass);
                assertThat(session.update(insert)).isEqualTo(1);

                // The annotated RESULT type is not a source: do not infer or create its local_user table.
                var query = QueryRequest.newInstance(
                        "select id,name from manual_entries where id=#{id}", Map.of("id", "1"), User.class);
                query.setFromClass(fromClass);
                assertThat(session.select(query)).singleElement().satisfies(user -> {
                    assertThat(user.getId()).isEqualTo("1");
                    assertThat(user.getName()).isEqualTo("alice");
                });
                assertThat(tableCount(source)).isZero();
            }
        }
    }

    @Test void countAndGroupCreateTheirSourceTable() {
        try (var source = source("count.db")) {
            var request = QueryRequest.newInstance(Map.class, new SelectWrapper<Map>().table("local_user"));
            request.setFromClass(User.class);
            assertThat(session(source).count(request)).isZero();
            assertThat(tableCount(source)).isEqualTo(1);
        }
        try (var source = source("group.db")) {
            var group = new GroupWrapper<Map>().table("local_user");
            group.metrics().count("id", "total");
            var request = QueryRequest.newInstance(Map.class, group);
            request.setFromClass(User.class);
            assertThat(session(source).group(request)).hasSize(1);
            assertThat(tableCount(source)).isEqualTo(1);
        }
    }

    @Test void emptyWriteDoesNotCreateTable() {
        try (var source = source("empty.db")) {
            assertThat(session(source).insert(ExecuteRequest.ofInsert(User.class))).isZero();
            assertThat(tableCount(source)).isZero();
        }
    }

    @Test void parsingSqlDoesNotBorrowAConnection() throws Exception {
        try (var source = source("parse.db")) {
            var observed = spy(source);
            var session = session(observed);
            var context = new SimpParamsContext(RequestType.select, Map.of(),
                    session.getCoderManager().createEncoderBuffer(), User.class, true);
            session.parseSql(new CommandNode<>("select id,name from local_user", Map.of(), ParseType.select), context);
            verify(observed, never()).getConnection();
            assertThat(tableCount(source)).isZero();
        }
    }

    @Test void rollbackDoesNotPublishUncommittedSchemaChecks() {
        try (var source = source("rollback.db")) {
            var session = session(source);
            session.startTransaction();
            try {
                assertThat(session.select(select())).isEmpty();
            } finally { session.rollbackTransaction(); }
            assertThat(tableCount(source)).isZero();
            assertThat(session.select(select())).isEmpty();
            assertThat(tableCount(source)).isEqualTo(1);
        }
    }

    @Test void failedFirstBatchRollsBackTableAndCanBeRetried() {
        try (var source = source("batch.db")) {
            var session = session(source);
            var request = ExecuteRequest.ofInsert(User.class);
            for (int index = 0; index < 513; index++) {
                request.addRequest(new CommandNode<>("insert into local_user(id,name) values(#{id},#{name})",
                        Map.of("id", index == 512 ? "0" : Integer.toString(index), "name", "batch"), ParseType.insert));
            }
            request.setBatch(true);
            assertThat(session.supportsParallelWrites()).isFalse();
            assertThat(session.getParallelConnections()).isEqualTo(1);
            assertThatThrownBy(() -> session.insert(request)).isInstanceOf(Exception.class);
            assertThat(tableCount(source)).isZero();
            assertThat(session.update(write("insert into local_user(id,name) values(#{id},#{name})",
                    Map.of("id", "0", "name", "retry")))).isEqualTo(1);
        }
    }

    @Test void nestedRollbackOnlyDoesNotPublishSchemaChecks() {
        try (var source = source("nested.db")) {
            var session = session(source);
            session.startTransaction();
            session.startTransaction();
            try {
                assertThat(session.select(select())).isEmpty();
                session.rollbackTransaction();
                assertThat(session.isOpenTransaction()).isTrue();
                assertThatThrownBy(session::commitTransaction).hasMessageContaining("rollback-only");
            } finally {
                while (session.isOpenTransaction()) session.rollbackTransaction();
            }
            assertThat(tableCount(source)).isZero();
            assertThat(session.select(select())).isEmpty();
            assertThat(tableCount(source)).isEqualTo(1);
        }
    }

    @Test void springRollbackAndCommitUseActualSpringCompletion() {
        try (var source = source("spring.db")) {
            var session = session(source);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            transaction.execute(status -> {
                assertThat(session.select(select())).isEmpty();
                assertThat(session.select(select())).isEmpty();
                status.setRollbackOnly();
                return null;
            });
            assertThat(tableCount(source)).isZero();
            transaction.execute(status -> {
                assertThat(session.select(select())).isEmpty();
                return null;
            });
            assertThat(tableCount(source)).isEqualTo(1);
            assertThat(session.select(select())).isEmpty();
        }
    }

    @Test void cachesAreNotSharedAcrossDatabaseFiles() {
        try (var first = source("first.db"); var second = source("second.db")) {
            assertThat(session(first).select(select())).isEmpty();
            assertThat(tableCount(second)).isZero();
            assertThat(session(second).select(select())).isEmpty();
            assertThat(tableCount(second)).isEqualTo(1);
        }
    }

    @Test void concurrentFirstRequestsWorkWithOnePooledConnection() throws Exception {
        try (var source = source("concurrent.db"); var callers = Executors.newFixedThreadPool(4)) {
            var session = session(source);
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                tasks.add(() -> session.select(select()).size());
            }
            for (var result : callers.invokeAll(tasks, 10, TimeUnit.SECONDS)) {
                assertThat(result.get()).isZero();
            }
            assertThat(tableCount(source)).isEqualTo(1);
        }
    }

    @JoinTable("local_user")
    public static class User {
        @JoinMeta(primaryKey = true) private String id;
        private String name;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
