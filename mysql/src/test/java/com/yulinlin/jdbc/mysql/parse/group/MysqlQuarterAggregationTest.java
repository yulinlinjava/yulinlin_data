package com.yulinlin.jdbc.mysql.parse.group;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.factory.ClassGroupWrapperFactory;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.mysql.MysqlParseManager;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the generated non-null quarter SQL in H2 MySQL mode, not a real MySQL server. */
class MysqlQuarterAggregationTest {
    @ParameterizedTest
    @CsvSource({
            "2025-12-31T23:59:59.999999, 2025-Q4",
            "2026-01-01T00:00:00, 2026-Q1", "2026-02-15T12:00:00, 2026-Q1",
            "2026-03-31T23:59:59.999999, 2026-Q1", "2026-04-01T00:00:00, 2026-Q2",
            "2026-05-15T12:00:00, 2026-Q2", "2026-06-30T23:59:59.999999, 2026-Q2",
            "2026-07-01T00:00:00, 2026-Q3", "2026-08-15T12:00:00, 2026-Q3",
            "2026-09-30T23:59:59.999999, 2026-Q3", "2026-10-01T00:00:00, 2026-Q4",
            "2026-11-15T12:00:00, 2026-Q4", "2026-12-31T23:59:59.999999, 2026-Q4",
            "2027-01-01T00:00:00, 2027-Q1", "2024-02-29T23:59:59.999999, 2024-Q1"
    })
    void groupsAllMonthsAndQuarterYearBoundaries(String input, String expected) throws Exception {
        String expression = expression(DateGroup.Type.quarter);
        assertEquals("CONCAT(YEAR(`event_time`), '-Q', QUARTER(`event_time`))", expression);
        try (var connection = DriverManager.getConnection(url());
             var query = connection.prepareStatement("SELECT " + expression
                     + " FROM (VALUES (CAST(? AS TIMESTAMP(6)))) t(event_time)")) {
            query.setObject(1, LocalDateTime.parse(input));
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(expected, rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void annotationAndFluentQueriesMergeMonthsButKeepDifferentYearsSeparate() throws Exception {
        GroupWrapper<?> annotated = (GroupWrapper<?>) new ClassGroupWrapperFactory(GroupWrapper.class)
                .create(new QuarterRow());
        var fluent = new GroupWrapper<>();
        fluent.table("quarter_events");
        fluent.aggregations().quarter("quarter", "quarter");
        fluent.metrics().sum("total", "total");
        annotated.orderByAsc("quarter");
        fluent.orderByAsc("quarter");
        SqlNode annotatedSql = sql(annotated);
        assertEquals(annotatedSql.getSql(), sql(fluent).getSql());
        try (var connection = DriverManager.getConnection(url()); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE quarter_events (event_time TIMESTAMP(6), amount INT)");
            try (var insert = connection.prepareStatement("INSERT INTO quarter_events VALUES (?, ?)")) {
                for (int year : new int[]{2025, 2026, 2027}) {
                    for (int month = 1; month <= 12; month++) {
                        insert.setObject(1, LocalDateTime.of(year, month, 15, 12, 30));
                        insert.setInt(2, 1);
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
            try (var rows = statement.executeQuery(annotatedSql.getSql())) {
                var actual = new ArrayList<String>();
                while (rows.next()) actual.add(rows.getString("quarter") + ":" + rows.getInt("total"));
                var expected = new ArrayList<String>();
                for (int year : new int[]{2025, 2026, 2027}) {
                    for (int quarter = 1; quarter <= 4; quarter++) expected.add(year + "-Q" + quarter + ":3");
                }
                assertEquals(expected, actual);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "minute, %Y-%m-%d %H:%i:00", "hour, %Y-%m-%d %H", "day, %Y-%m-%d",
            "month, %Y-%m", "year, %Y"
    })
    void otherDateGranularitiesKeepTheirExistingFormats(DateGroup.Type type, String format) {
        assertEquals("DATE_FORMAT(`event_time`,'" + format + "')", expression(type));
    }

    private static String expression(DateGroup.Type type) {
        return (String) new MysqlParseManager().parse(new DateGroup("quarter", type), context());
    }

    private static SimpParamsContext context() {
        return new SimpParamsContext(RequestType.group, Map.of(), new JdbcCoderManager().createEncoderBuffer(),
                QuarterRow.class, true);
    }

    private static SqlNode sql(GroupWrapper<?> wrapper) {
        return (SqlNode) ((ParseResult) new MysqlParseManager().parse(wrapper, context())).getRequest();
    }

    private static String url() {
        return "jdbc:h2:mem:mysql_quarter_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    }

    @JoinTable("quarter_events")
    public static class QuarterRow {
        @JoinField(name = "event_time")
        @JoinAggregations(AggregationsEnum.quarter)
        public String quarter;

        @JoinField(name = "amount")
        @JoinMetrics(MetricsEnum.sum)
        public long total;
    }
}
