package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.data.lang.json.JsonUtil;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static com.yulinlin.jdbc.postgresql.PostgresqlTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "pg.test.url", matches = "jdbc:postgresql:.*")
class PostgresqlJsonStorageIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"jsonb", "json", "text"})
    void wholeColumnRoundTripAndReplacementRemainSupported(String type) throws Exception {
        try (var connection = samples(type)) {
            Map<String, Object> original = Map.of("name", "中文 ' #{name} ->", "enabled", false,
                    "items", List.of(1, 2, 3));
            var insert = new InsertWrapper<>().table("pg_temp.pg_samples");
            insert.fields().field("id", 1L).field("payloadData", original);
            try (var statement = prepare(connection, sql(insert, RequestType.insert))) {
                assertEquals(1, statement.executeUpdate());
            }
            assertEquals(original, read(connection));
            Map<String, Object> replacement = Map.of("profile", Map.of("name", "Alice"));
            var update = new UpdateWrapper<>().table("pg_temp.pg_samples");
            update.fields().field("payloadData", replacement);
            update.where().eq("id", 1L);
            try (var statement = prepare(connection, sql(update, RequestType.update))) {
                assertEquals(1, statement.executeUpdate());
            }
            assertEquals(replacement, read(connection), "Whole-column replacement must not merge the old JSON");
        }
    }

    @Test
    void nativeJsonReadAndPartialWriteStillExecuteAsParameterizedSql() throws Exception {
        try (var connection = samples("jsonb"); var seed = connection.createStatement()) {
            seed.execute("INSERT INTO pg_temp.pg_samples VALUES (1, '{\"profile\":{},\"keep\":42}')");
            String key = "a'\\\"#{0}${missing},{}[]";
            String value = "O'Reilly 中文 ' OR 1=1 --";
            var update = CommandNode.of("UPDATE pg_temp.pg_samples SET document_data = jsonb_set(document_data, "
                            + "ARRAY['profile', CAST(#{key} AS text)], CAST(#{json} AS jsonb), true) WHERE id = #{id}",
                    Map.of("key", key, "json", JsonUtil.toJson(value), "id", 1L), ParseType.update);
            var updateSql = sql(update, RequestType.update);
            assertFalse(updateSql.getSql().contains(value));
            try (var statement = prepare(connection, updateSql)) {
                assertEquals(1, statement.executeUpdate());
            }
            var select = CommandNode.of("SELECT document_data->'profile'->>CAST(#{key} AS text)"
                            + " FROM pg_temp.pg_samples WHERE id = #{id}", Map.of("key", key, "id", 1L), ParseType.select);
            assertEquals(List.of(value), query(connection, sql(select, RequestType.select)));
            assertEquals(Map.of("profile", Map.of(key, value), "keep", 42), read(connection));
        }
    }

    @Test
    void legacyPathCallsFailBeforeAnyStatementIsExecuted() throws Exception {
        try (var connection = samples("jsonb"); var seed = connection.createStatement()) {
            seed.execute("INSERT INTO pg_temp.pg_samples VALUES (1, '{\"name\":\"unchanged\"}')");
            var update = new UpdateWrapper<>().table("pg_temp.pg_samples");
            update.fields().field("payloadData->name", "changed");
            assertThrows(UnsupportedOperationException.class, () -> sql(update, RequestType.update));
            var select = new SelectWrapper<>().table("pg_temp.pg_samples");
            select.where().eq("payloadData->name", "unchanged");
            assertThrows(UnsupportedOperationException.class, () -> sql(select, RequestType.select));
            assertEquals(Map.of("name", "unchanged"), read(connection));
        }
    }

    private static Map<String, Object> read(Connection connection) throws Exception {
        var session = new PostgresqlSession(new PGSimpleDataSource());
        session.setCoderManager(new PostgresqlCoderManager());
        var select = new SelectWrapper<>().table("pg_temp.pg_samples");
        select.fields().field("payloadData");
        select.where().eq("id", 1L);
        var rows = session.executeSelectNode(connection, sql(select, RequestType.select));
        assertEquals(1, rows.size());
        return rows.getFirst().getObject("payloadData", Row.class.getField("payloadData"));
    }

    private static Connection samples(String type) throws Exception {
        var connection = connect();
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pg_samples (id bigint PRIMARY KEY, document_data " + type + ")");
        } catch (Exception error) {
            connection.close();
            throw error;
        }
        return connection;
    }
}
