package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

/** Opt in with PG_TEST_URL; uses only a connection-local temporary table. Never uses application credentials. */
@EnabledIfEnvironmentVariable(named = "PG_TEST_URL", matches = "jdbc:postgresql:.*")
class PostgresqlServerTest {
    private final PostgresqlSession parserSession = new PostgresqlSession(null);
    private final JdbcCoderManager coder = new JdbcCoderManager();
    private ParseResult parse(INode wrapper, RequestType type) {
        return (ParseResult) parserSession.parseSql(wrapper,
                new SimpParamsContext(type, Map.of(), coder.createEncoderBuffer(), Map.class, false));
    }

    @Test void realServerCrudCodecsJsonGroupingBatchAndRollback() throws Exception {
        var properties = new Properties();
        properties.setProperty("user", System.getenv().getOrDefault("PG_TEST_USER", "postgres"));
        properties.setProperty("password", System.getenv().getOrDefault("PG_TEST_PASSWORD", ""));
        properties.setProperty("stringtype", "unspecified");
        properties.setProperty("reWriteBatchedInserts", "true");
        try (Connection connection = DriverManager.getConnection(System.getenv("PG_TEST_URL"), properties)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE pg_probe(id text primary key, user_name text, created_at text, "
                        + "amount numeric(30,4), payload jsonb, labels jsonb, state text, enabled boolean) ON COMMIT PRESERVE ROWS");
            }
            var session = new ProbeSession();
            session.setCoderManager(coder); session.setExecuteBatchSize(1);
            Date createdAt = DateTime.parse("2026-10-05 12:30:00").toDate();
            var amount = new BigDecimal("12345678901234567890.1234");
            var first = insert("1", "alice", createdAt, amount);
            var second = insert("2", "bob", createdAt, amount);
            assertThat(session.write(connection, List.of(first, second))).isEqualTo(2);
            connection.commit();

            var query = new SelectWrapper<>().table("pg_probe").page(1, 1).orderBy("id", true).lock();
            query.fields().field("id", "id").field("user_name", "userName").field("created_at", "createdAt")
                    .field("amount", "amount").field("payload", "payload").field("labels", "labels").field("state", "state")
                    .field("enabled", "enabled");
            query.where().gte("payload->age", 18).eq("payload->enabled", true)
                    .gte("created_at", DateTime.parse("2026-10-05 00:00:00").toDate());
            var rows = session.executeSelectNode(connection, (SqlNode) parse(query, RequestType.select).getRequest());
            assertThat(rows).hasSize(1);
            Row row = (Row) rows.getFirst().decode(Row.class);
            assertThat(row.getUserName()).isEqualTo("alice");
            assertThat(row.getCreatedAt()).isEqualTo(createdAt);
            assertThat(row.getAmount()).isEqualTo(amount);
            assertThat(row.getPayload()).containsEntry("age", 20).containsEntry("enabled", true);
            assertThat(row.getLabels()).containsExactly("a", "b");
            assertThat(row.getState()).isEqualTo(State.READY);
            assertThat(row.getEnabled()).isFalse();
            assertThat(select(connection, new CountWrapper(query), RequestType.count).getFirst()
                    .<String>getObject("total")).isEqualTo("2");

            var group = new GroupWrapper<>().table("pg_probe");
            group.aggregations().day("created_at", "day");
            group.metrics().sum("amount", "sumAmount");
            group.having().gt("sumAmount", BigDecimal.ONE);
            assertThat(select(connection, group, RequestType.group)).hasSize(1);
            assertThat(select(connection, new CountWrapper(group), RequestType.count).getFirst()
                    .<String>getObject("total")).isEqualTo("1");

            var update = new UpdateWrapper<>().table("pg_probe");
            update.fields().field("user_name", "updated"); update.where().eq("id", "1");
            assertThat(session.write(connection, List.of(parse(update, RequestType.update)))).isEqualTo(1);
            var delete = new DeleteWrapper<>().table("pg_probe"); delete.where().eq("id", "2");
            assertThat(session.write(connection, List.of(parse(delete, RequestType.delete)))).isEqualTo(1);
            connection.rollback();
            assertThat(select(connection, new CountWrapper(query), RequestType.count).getFirst()
                    .<String>getObject("total")).isEqualTo("2");

            assertThatThrownBy(() -> session.write(connection,
                    List.of(insert("3", "new", createdAt, amount), insert("1", "duplicate", createdAt, amount))))
                    .isInstanceOf(java.sql.SQLException.class);
            connection.rollback();
            assertThat(select(connection, new CountWrapper(query), RequestType.count).getFirst()
                    .<String>getObject("total")).isEqualTo("2");
        }
    }

    private ParseResult insert(String id, String name, Date date, BigDecimal amount) {
        var wrapper = new InsertWrapper<>().table("pg_probe");
        wrapper.fields().field("id", id).field("user_name", name).field("created_at", date).field("amount", amount)
                .field("payload", Map.of("age", 20, "enabled", true)).field("labels", List.of("a", "b"))
                .field("state", State.READY).field("enabled", false);
        return parse(wrapper, RequestType.insert);
    }

    private List<IDataBuffer> select(Connection connection, INode wrapper, RequestType type) {
        var session = new ProbeSession(); session.setCoderManager(coder);
        return session.executeSelectNode(connection, (SqlNode) parse(wrapper, type).getRequest());
    }

    private static class ProbeSession extends PostgresqlSession {
        ProbeSession() { super(null); }
        int write(Connection connection, List<ParseResult> nodes) { return executeUpdateNode(connection, nodes); }
    }

    public enum State { READY }
    public static class Row {
        private String id;
        private String userName;
        private Date createdAt;
        private BigDecimal amount;
        private Map<String, Object> payload;
        private List<String> labels;
        private State state;
        private Boolean enabled;
        public String getId() { return id; }
        public void setId(String value) { id = value; }
        public String getUserName() { return userName; }
        public void setUserName(String value) { userName = value; }
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date value) { createdAt = value; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal value) { amount = value; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> value) { payload = value; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> value) { labels = value; }
        public State getState() { return state; }
        public void setState(State value) { state = value; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean value) { enabled = value; }
    }
}
