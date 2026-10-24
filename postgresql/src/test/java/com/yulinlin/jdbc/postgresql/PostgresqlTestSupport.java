package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import com.yulinlin.jdbc.session.SqlNode;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Opt-in real PostgreSQL tests. Every table is connection-local and in pg_temp. */
public final class PostgresqlTestSupport {
    private PostgresqlTestSupport() {}

    public static SqlNode sql(INode node, RequestType type) {
        var context = new SimpParamsContext(type, Map.of(), new PostgresqlCoderManager().createEncoderBuffer(),
                Row.class, true);
        return (SqlNode) ((ParseResult) new PostgresqlParseManager().parse(node, context)).getRequest();
    }

    public static Connection connect() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", System.getProperty("pg.test.user", "postgres"));
        properties.setProperty("password", System.getenv().getOrDefault("PG_TEST_PASSWORD", ""));
        return DriverManager.getConnection(System.getProperty("pg.test.url"), properties);
    }

    public static PreparedStatement prepare(Connection connection, SqlNode sql) throws Exception {
        var statement = connection.prepareStatement(sql.getSql());
        try {
            for (int i = 0; i < sql.getList().size(); i++) statement.setObject(i + 1, sql.getList().get(i));
            return statement;
        } catch (Exception error) {
            statement.close();
            throw error;
        }
    }

    public static List<String> query(Connection connection, SqlNode sql) throws Exception {
        try (var statement = prepare(connection, sql); var rows = statement.executeQuery()) {
            var result = new ArrayList<String>();
            while (rows.next()) {
                var values = new ArrayList<String>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    Object value = rows.getObject(i);
                    values.add(value == null ? "NULL" : value instanceof BigDecimal decimal
                            ? decimal.stripTrailingZeros().toPlainString() : value.toString());
                }
                result.add(String.join("|", values));
            }
            return result;
        }
    }

    public static class Row {
        public Long id;
        @JoinField(name = "document_data")
        public Map<String, Object> payloadData;
        public Map<String, Object> detailData;
        public BigDecimal amount;
        @JoinField(name = "amount")
        public BigDecimal total;
        public String eventTime;
        public String category;
    }
}
