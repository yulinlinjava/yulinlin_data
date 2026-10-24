package com.yulinlin.jdbc.h2.parse.group;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.factory.ClassGroupWrapperFactory;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.h2.H2ParseManager;
import com.yulinlin.jdbc.session.SqlNode;
import com.yulinlin.jdbc.sql.parse.AliasUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class H2HavingAliasTest {
    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MySQL"})
    void annotationAndFluentHavingFilterBucketKeysAndMetrics(String mode) throws Exception {
        GroupWrapper<?> annotated = (GroupWrapper<?>) new ClassGroupWrapperFactory(GroupWrapper.class)
                .create(new SampleRow());
        GroupWrapper<?> fluent = intervals("bucket");
        for (GroupWrapper<?> wrapper : List.of(annotated, fluent)) {
            wrapper.having(h -> h.gt("bucket", 0).lte("bucket", 20).gt("total", 10));
            wrapper.orderByAsc("bucket");
        }
        SqlNode node = sql(annotated);
        assertEquals(node.getSql(), sql(fluent).getSql());
        assertEquals(List.of(0, 20, 10), node.getList());
        assertTrue(node.getSql().contains("MIN(CAST(") || node.getSql().contains("MIN((CAST("));
        assertTrue(node.getSql().contains("sum(\"amount\") >"));
        assertFalse(node.getSql().contains("MIN(sum("));
        try (var connection = samples(mode)) {
            assertEquals(List.of("20|29"), execute(connection, node));
        }
        assertTrue(AliasUtil.ok(), "HAVING context must not leak into subsequent queries");
    }

    @ParameterizedTest
    @CsvSource({
            "minute, 2026-10-02 12:00:00, 19",
            "hour, 2026-10-02 12, 19",
            "day, 2026-10-02, 29",
            "month, 2026-10, 37",
            "quarter, 2026-Q4, 37",
            "year, 2026, 27"
    })
    void filtersEveryDateGroupingKey(AggregationsEnum type, String key, String total) throws Exception {
        var wrapper = base();
        wrapper.aggregations().field(type, "eventTime", "period");
        wrapper.metrics().sum("total", "total");
        wrapper.having(h -> h.eq("period", key).gt("total", 0));
        SqlNode node = sql(wrapper);
        assertEquals(List.of(key, 0), node.getList());
        assertFalse(node.getSql().contains(key), "HAVING values must remain bound parameters");
        try (var connection = samples("")) {
            assertEquals(List.of(key + "|" + total), execute(connection, node));
        }
    }

    @ParameterizedTest
    @CsvSource({"sum, 49", "count, 4", "avg, 12.25", "min, 0", "max, 20", "distinctCount, 4"})
    void expandsEveryMetricWithoutNestingAggregates(MetricsEnum type, String expected) throws Exception {
        var wrapper = base();
        wrapper.aggregations().field("category", "categoryKey");
        wrapper.metrics().field(type, "bucket", "metric");
        wrapper.having(h -> h.eq("categoryKey", "B").eq("metric", new BigDecimal(expected)));
        try (var connection = samples("")) {
            assertEquals(List.of("B|" + expected), execute(connection, sql(wrapper)));
        }
    }

    @Test
    void whereUsesOriginalColumnAndHavingUsesBucketEvenWhenAliasMatchesColumn() throws Exception {
        var wrapper = intervals("amount");
        wrapper.where(w -> w.lt("bucket", 20));
        wrapper.having(h -> h.gt("amount", 10));
        wrapper.orderByDesc("amount");
        wrapper.page(1, 1);
        SqlNode node = sql(wrapper);
        assertTrue(node.getSql().contains("where \"amount\" <"));
        assertFalse(node.getSql().contains("group by \"amount\""));
        assertTrue(node.getSql().contains("order by \"amount\" desc"));
        assertEquals(List.of(20, 10), node.getList());
        try (var connection = samples("")) {
            assertEquals(List.of("20|29"), execute(connection, node));
        }
    }

    @Test
    void multipleDimensionsKeepWhereHavingAndOrderingSeparate() throws Exception {
        var wrapper = base();
        wrapper.aggregations().field("category", "categoryKey").day("eventTime", "dayKey");
        wrapper.metrics().sum("total", "total");
        wrapper.where(w -> w.gte("eventTime", "2026-10-01 00:00:00"));
        wrapper.having(h -> h.eq("categoryKey", "B").eq("dayKey", "2026-10-02").gte("total", 29));
        wrapper.orderByAsc("categoryKey").orderByAsc("dayKey");
        try (var connection = samples("")) {
            assertEquals(List.of("B|2026-10-02|29"), execute(connection, sql(wrapper)));
        }
    }

    @Test
    void nestedBooleanConditionsAndGroupCountUseTheSameHavingSemantics() throws Exception {
        var wrapper = intervals("bucket");
        wrapper.having(h -> h.and(a -> a.gte("bucket", 10).lte("bucket", 30))
                .or(o -> o.gt("total", 20).eq("bucket", 30)));
        wrapper.orderByAsc("bucket");
        try (var connection = samples("")) {
            assertEquals(List.of("20|29", "30|20"), execute(connection, sql(wrapper)));
            assertEquals(List.of("2"), execute(connection, sql(new CountWrapper(wrapper))));
        }
    }

    @Test
    void nullGroupingKeyRemainsNullInHaving() throws Exception {
        var wrapper = intervals("bucket");
        wrapper.having(h -> h.isNull("bucket"));
        try (var connection = samples("")) {
            assertEquals(List.of("NULL|NULL"), execute(connection, sql(wrapper)));
        }
    }

    @Test
    void supportsQuotedAliasesAndDoesNotInlineFilterValues() throws Exception {
        var wrapper = base();
        String alias = "group \"Key\"";
        wrapper.aggregations().field("category", alias);
        wrapper.metrics().sum("total", "total");
        String value = "B' OR 1=1 --";
        wrapper.having(h -> h.eq(alias, value));
        SqlNode node = sql(wrapper);
        assertEquals(List.of(value), node.getList());
        assertTrue(node.getSql().contains("as \"group \"\"Key\"\"\""));
        assertFalse(node.getSql().contains(value));
        try (var connection = samples("")) {
            assertEquals(List.of(), execute(connection, node));
        }
    }

    @Test
    void aggregateOnlyHavingStillWorks() throws Exception {
        var wrapper = base();
        wrapper.metrics().sum("total", "total");
        wrapper.having(h -> h.eq("total", 47));
        SqlNode node = sql(wrapper);
        assertFalse(node.getSql().contains("MIN("));
        assertFalse(node.getSql().contains("group by"));
        try (var connection = samples("")) {
            assertEquals(List.of("47"), execute(connection, node));
        }
    }

    @Test
    void groupExpressionMetadataDoesNotLeakBetweenRequests() throws Exception {
        var parser = new H2ParseManager();
        var grouped = intervals("shared");
        grouped.having(h -> h.eq("shared", 10));
        var metric = base();
        metric.metrics().sum("total", "shared");
        metric.having(h -> h.eq("shared", 47));
        SqlNode first = sql(parser, grouped);
        SqlNode second = sql(parser, metric);
        assertTrue(first.getSql().contains("MIN("));
        assertFalse(second.getSql().contains("MIN("));
        try (var connection = samples("")) {
            assertEquals(List.of("10|9"), execute(connection, first));
            assertEquals(List.of("47"), execute(connection, second));
        }
    }

    private static GroupWrapper<Object> base() {
        return new GroupWrapper<>().table("having_samples");
    }

    private static GroupWrapper<Object> intervals(String alias) {
        var wrapper = base();
        wrapper.aggregations().interval("bucket", alias, 10);
        wrapper.metrics().sum("total", "total");
        return wrapper;
    }

    private static SqlNode sql(INode node) {
        return sql(new H2ParseManager(), node);
    }

    private static SqlNode sql(H2ParseManager parser, INode node) {
        var context = new SimpParamsContext(node instanceof CountWrapper ? RequestType.count : RequestType.group,
                Map.of(), new JdbcCoderManager().createEncoderBuffer(), SampleRow.class, true);
        return (SqlNode) ((ParseResult) parser.parse(node, context)).getRequest();
    }

    private static List<String> execute(Connection connection, SqlNode node) throws Exception {
        try (var query = connection.prepareStatement(node.getSql())) {
            for (int i = 0; i < node.getList().size(); i++) query.setObject(i + 1, node.getList().get(i));
            try (var rows = query.executeQuery()) {
                var values = new ArrayList<String>();
                while (rows.next()) {
                    var columns = new ArrayList<String>();
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        Object value = rows.getObject(i);
                        columns.add(value == null ? "NULL" : value instanceof BigDecimal decimal
                                ? decimal.stripTrailingZeros().toPlainString() : value.toString());
                    }
                    values.add(String.join("|", columns));
                }
                return values;
            }
        }
    }

    private static Connection samples(String mode) throws Exception {
        var connection = DriverManager.getConnection("jdbc:h2:mem:having_" + UUID.randomUUID() + mode);
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE \"having_samples\" (\"amount\" NUMERIC(20, 2),"
                    + " \"event_time\" TIMESTAMP(6), \"category\" VARCHAR(40))");
            statement.execute("INSERT INTO \"having_samples\" VALUES"
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

    @JoinTable("having_samples")
    public static class SampleRow {
        @JoinField(name = "amount")
        @JoinAggregations(value = AggregationsEnum.interval, interval = 10)
        public BigDecimal bucket;

        @JoinField(name = "amount")
        @JoinMetrics(MetricsEnum.sum)
        public BigDecimal total;

        @JoinField(name = "event_time")
        public String eventTime;

        public String category;
    }
}
