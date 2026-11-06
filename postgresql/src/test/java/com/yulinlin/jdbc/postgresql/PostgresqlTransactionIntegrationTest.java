package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.exception.DuplicateDataException;
import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "pg.test.url", matches = "jdbc:postgresql:.*")
class PostgresqlTransactionIntegrationTest {
    @Test void realPgAbortedStatementRecoversAtSavepointAndCommitsOuterRows() throws Exception {
        try (Connection connection = PostgresqlTestSupport.connect()) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE orm_savepoint_rows (id INTEGER PRIMARY KEY)");
            }
            var session = new PostgresqlSession(new SingleConnectionDataSource(connection, true));
            session.setGroup("pg");
            session.setCoderManager(new PostgresqlCoderManager());
            var route = RouteSession.builder().build();
            route.setLoadBalance(new RandomLoadBalance());
            route.registerSession(session);
            try {
                route.transaction(() -> {
                    insert(route, 1);
                    assertThrows(DuplicateDataException.class, () -> route.transaction(() -> {
                        insert(route, 2);
                        insert(route, 1);
                        return null;
                    }));
                    assertFalse(route.isRollbackOnly());
                    // 即使内层捕获语句错误，不能直接提交失败范围。
                    assertThrows(OrmException.class, () -> route.transaction(() -> {
                        insert(route, 4);
                        assertThrows(DuplicateDataException.class, () -> insert(route, 1));
                        return null;
                    }));
                    insert(route, 3);
                    return null;
                });
                List<Integer> ids = new ArrayList<>();
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT id FROM pg_temp.orm_savepoint_rows ORDER BY id")) {
                    while (rows.next()) ids.add(rows.getInt(1));
                }
                assertEquals(List.of(1, 3), ids);
                assertFalse(route.isOpenTransaction());
            } finally { session.getThreadPoolExecutor().shutdownNow(); }
        }
    }
    private void insert(RouteSession route, int id) {
        var wrapper = new InsertWrapper<>().table("pg_temp.orm_savepoint_rows");
        wrapper.fields().field("id", id);
        var request = ExecuteRequest.ofInsert(Object.class);
        request.addRequest(wrapper);
        route.insert(request);
    }
}
