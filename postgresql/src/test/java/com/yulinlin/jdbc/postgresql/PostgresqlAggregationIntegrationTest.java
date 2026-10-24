package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.anno.AggregationsEnum;
import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.yulinlin.jdbc.postgresql.PostgresqlTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "pg.test.url", matches = "jdbc:postgresql:.*")
class PostgresqlAggregationIntegrationTest {
    @ParameterizedTest
    @CsvSource({"2.99999999999999999999, 3, 3", "3, 3, 6", "3.00000000000000000001, 3, 6",
            "-3.00000000000000000001, 3, -3", "-3, 3, 0", "-2.99999999999999999999, 3, 0",
            "-0.00000000000000000001, 3, 0", "0, 3, 3", "0.00000000000000000001, 3, 3",
            "9.99999999999999999999, 10, 10", "10, 10, 20", "-10.00000000000000000001, 10, -10",
            "-10, 10, 0", "-9.99999999999999999999, 10, 0",
            "9223372036854775807, 10, 9223372036854775810",
            "-9223372036854775808, 10, -9223372036854775800",
            "2147483647, 2147483647, 4294967294", "NULL, 10, NULL"})
    void intervalBoundariesAreExact(String value, int step, String expected) throws Exception {
        try (var connection = connect(); var statement = connection.prepareStatement(
                "SELECT " + interval(step) + " FROM (VALUES (CAST(? AS numeric))) AS t(amount)")) {
            statement.setBigDecimal(1, value.equals("NULL") ? null : new BigDecimal(value));
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                BigDecimal actual = rows.getBigDecimal(1);
                if (expected.equals("NULL")) assertNull(actual);
                else assertEquals(0, new BigDecimal(expected).compareTo(actual));
            }
        }
    }

    @Test
    void randomizedHighPrecisionIntervalsMatchJavaFloorDefinition() throws Exception {
        var random = new Random(20261024);
        try (var connection = connect()) {
            for (int step : new int[]{1, 3, 7, 10, 99, Integer.MAX_VALUE}) {
                try (var statement = connection.prepareStatement(
                        "SELECT " + interval(step) + " FROM (VALUES (CAST(? AS numeric))) AS t(amount)")) {
                    for (int i = 0; i < 100; i++) {
                        BigDecimal value = BigDecimal.valueOf(random.nextLong(), random.nextInt(30));
                        BigDecimal size = BigDecimal.valueOf(step);
                        BigDecimal expected = value.divide(size, 0, RoundingMode.FLOOR).add(BigDecimal.ONE).multiply(size);
                        statement.setBigDecimal(1, value);
                        try (var rows = statement.executeQuery()) {
                            assertTrue(rows.next());
                            assertEquals(0, expected.compareTo(rows.getBigDecimal(1)), value + "/" + step);
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"minute, 2026-10-02 12:00:00, 19", "hour, 2026-10-02 12, 19",
            "day, 2026-10-02, 29", "month, 2026-10, 37", "quarter, 2026-10, 37", "year, 2026, 27"})
    void everyDateGroupingSupportsHaving(AggregationsEnum type, String period, String total) throws Exception {
        var group = new GroupWrapper<>().table("pg_temp.pg_samples");
        group.aggregations().field(type, "eventTime", "period");
        group.metrics().sum("amount", "total");
        group.having(h -> h.eq("period", period).gt("total", 0));
        try (var connection = samples()) {
            assertEquals(List.of(period + "|" + total), query(connection, sql(group, RequestType.group)));
        }
    }

    @Test
    void intervalHavingAliasCollisionPaginationAndGroupCountWorkTogether() throws Exception {
        var group = new GroupWrapper<>().table("pg_temp.pg_samples");
        group.aggregations().interval("amount", "amount", 10);
        group.metrics().sum("total", "total");
        group.where().lt("amount", 20);
        group.having(h -> h.gt("amount", 10).gt("total", 20));
        group.orderByDesc("amount").page(1, 1);
        try (var connection = samples()) {
            assertEquals(List.of("20|29"), query(connection, sql(group, RequestType.group)));
            assertEquals(List.of("1"), query(connection, sql(new CountWrapper(group), RequestType.count)));
        }
    }

    private static String interval(int step) {
        var context = new SimpParamsContext(RequestType.group, Map.of(),
                new PostgresqlCoderManager().createEncoderBuffer(), Row.class, true);
        return new PostgresqlParseManager().parse(new IntervalGroup("amount", step), context).toString();
    }

    private static Connection samples() throws Exception {
        var connection = connect();
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pg_samples (amount numeric, event_time timestamp, category text)");
            statement.execute("INSERT INTO pg_temp.pg_samples VALUES"
                    + " (-10, '2026-09-30 23:59:59', 'A'), (-1, '2026-10-01 00:00:00', 'A'),"
                    + " (0, '2026-10-01 01:00:00', 'B'), (9, '2026-10-01 12:00:00', 'A'),"
                    + " (10, '2026-10-02 00:00:00', 'B'), (19, '2026-10-02 12:00:00', 'B'),"
                    + " (20, '2027-01-01 00:00:00', 'B'), (NULL, NULL, 'N')");
        } catch (Exception error) {
            connection.close();
            throw error;
        }
        return connection;
    }
}
