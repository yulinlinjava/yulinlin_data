package com.yulinlin.jdbc.h2.parse.group;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.factory.ClassGroupWrapperFactory;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.h2.H2ParseManager;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class H2IntervalAggregationTest {
    @ParameterizedTest
    @CsvSource({
            "-20, 10, -10", "-10.01, 10, -10", "-10, 10, 0", "-9, 10, 0",
            "-1, 10, 0", "-0.01, 10, 0", "0, 10, 10", "1, 10, 10",
            "9, 10, 10", "9.99, 10, 10", "10, 10, 20", "10.01, 10, 20",
            "12, 10, 20", "19, 10, 20", "19.99, 10, 20", "20, 10, 30",
            "-1, 1, 0", "0, 1, 1", "1, 1, 2", "-5, 5, 0", "5, 5, 10",
            "-3.00001, 3, -3", "-3, 3, 0", "2.99999, 3, 3", "3, 3, 6",
            "2147483647, 2147483647, 4294967294",
            "-2147483647, 2147483647, 0",
            "9223372036854775807, 10, 9223372036854775810",
            "-9223372036854775808, 10, -9223372036854775800",
            "9.9999999999999999999999999999999999999999, 10, 10",
            "-0.0000000000000000000000000000000000000001, 10, 0",
            "-10.0000000000000000000000000000000000000001, 10, -10",
            "9999999999999999999999999999999999999999.9999, 10, 10000000000000000000000000000000000000000"
    })
    void keepsLeftClosedRightOpenBoundaries(String value, int step, String expected) throws Exception {
        try (var connection = connection();
             var query = connection.prepareStatement(singleValueSql(step))) {
            query.setBigDecimal(1, new BigDecimal(value));
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertDecimal(expected, rows.getBigDecimal(1));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void nullRemainsNull() throws Exception {
        try (var connection = connection();
             var query = connection.prepareStatement(singleValueSql(10))) {
            query.setNull(1, Types.DECIMAL);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertNull(rows.getBigDecimal(1));
                assertTrue(rows.wasNull());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonPositiveStepsBeforeExecution(int step) {
        var error = assertThrows(IllegalArgumentException.class, () -> expression(step));
        assertEquals("Interval must be positive", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTEGER", "BIGINT", "NUMERIC(80, 40)", "DECFLOAT", "DOUBLE PRECISION"})
    void handlesNativeNumericColumnTypes(String type) throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE samples (\"amount\" " + type + ")");
            try (var insert = connection.prepareStatement("INSERT INTO samples VALUES (?)")) {
                for (long value : new long[]{-20, -19, -10, -9, -1, 0, 1, 9, 10, 19, 20}) {
                    insert.setLong(1, value);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (var rows = statement.executeQuery("SELECT \"amount\", " + expression(10) + " FROM samples")) {
                while (rows.next()) {
                    assertDecimal(expected(rows.getBigDecimal(1), 10).toPlainString(), rows.getBigDecimal(2));
                }
            }
        }
    }

    @Test
    void agreesWithExactDecimalFloorForDifferentSteps() throws Exception {
        var random = new Random(20261024);
        try (var connection = connection()) {
            for (int step : new int[]{1, 3, 5, 10, 97, 1000000007, Integer.MAX_VALUE}) {
                try (var query = connection.prepareStatement(singleValueSql(step))) {
                    for (int i = 0; i < 150; i++) {
                        BigDecimal value = BigDecimal.valueOf(random.nextLong(), i % 19);
                        query.setBigDecimal(1, value);
                        try (var rows = query.executeQuery()) {
                            assertTrue(rows.next());
                            assertDecimal(expected(value, step).toPlainString(), rows.getBigDecimal(1));
                        }
                    }
                }
            }
        }
    }

    @Test
    void annotationAndFluentQueriesGroupOrderAndFilterMetricsIdentically() throws Exception {
        GroupWrapper<?> annotated = (GroupWrapper<?>) new ClassGroupWrapperFactory(GroupWrapper.class)
                .create(new IntervalRow());
        var fluent = new GroupWrapper<>();
        fluent.table("interval_samples");
        fluent.aggregations().interval("upper", "upper", 10);
        fluent.metrics().sum("total", "total");
        for (GroupWrapper<?> wrapper : List.of(annotated, fluent)) {
            wrapper.orderByAsc("upper");
            wrapper.having(h -> h.gt("total", -12));
        }
        SqlNode annotatedSql = sql(annotated);
        SqlNode fluentSql = sql(fluent);
        assertEquals(annotatedSql.getSql(), fluentSql.getSql());
        assertEquals(annotatedSql.getList(), fluentSql.getList());
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE \"interval_samples\" (\"amount\" NUMERIC(20, 2))");
            statement.execute("INSERT INTO \"interval_samples\" VALUES (-20), (-10.01), (-10), (-1),"
                    + " (0), (9.99), (10), (12), (19), (20), (NULL)");
            try (var query = connection.prepareStatement(annotatedSql.getSql())) {
                for (int i = 0; i < annotatedSql.getList().size(); i++) {
                    query.setObject(i + 1, annotatedSql.getList().get(i));
                }
                try (var rows = query.executeQuery()) {
                    var actual = new ArrayList<String>();
                    while (rows.next()) {
                        actual.add(rows.getBigDecimal("upper").stripTrailingZeros().toPlainString() + ":"
                                + rows.getBigDecimal("total").stripTrailingZeros().toPlainString());
                    }
                    assertEquals(List.of("0:-11", "10:9.99", "20:41", "30:20"), actual);
                }
            }
        }
    }

    private static String expression(int step) {
        return (String) new H2ParseManager().parse(new IntervalGroup("upper", step), context());
    }

    private static String singleValueSql(int step) {
        return "SELECT " + expression(step) + " FROM (VALUES (CAST(? AS DECFLOAT))) t(\"amount\")";
    }

    private static SimpParamsContext context() {
        return new SimpParamsContext(RequestType.group, Map.of(), new JdbcCoderManager().createEncoderBuffer(),
                IntervalRow.class, true);
    }

    private static SqlNode sql(GroupWrapper<?> wrapper) {
        return (SqlNode) ((ParseResult) new H2ParseManager().parse(wrapper, context())).getRequest();
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:h2:mem:interval_" + UUID.randomUUID());
    }

    private static BigDecimal expected(BigDecimal value, int step) {
        BigDecimal width = BigDecimal.valueOf(step);
        return value.divide(width, 0, RoundingMode.FLOOR).add(BigDecimal.ONE).multiply(width);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " != " + actual);
    }

    @JoinTable("interval_samples")
    public static class IntervalRow {
        @JoinField(name = "amount")
        @JoinAggregations(value = AggregationsEnum.interval, interval = 10)
        public BigDecimal upper;

        @JoinField(name = "amount")
        @JoinMetrics(MetricsEnum.sum)
        public BigDecimal total;
    }
}
