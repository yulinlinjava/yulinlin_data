package com.yulinlin.jdbc.sql;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.enums.SqlKeys;
import com.yulinlin.jdbc.session.SqlNode;
import com.yulinlin.jdbc.sql.parse.AliasUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcJsonBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {"payload->name", "payload->items->0", "payload->profile.name", "payload->"})
    void rejectsLegacyPathsInEveryAutomaticSqlPosition(String path) {
        var select = new SelectWrapper<>().table("samples");
        select.where().eq(path, "Alice");
        rejected(select, RequestType.select);
        rejected(new CountWrapper(select), RequestType.count);

        var fields = new SelectWrapper<>().table("samples");
        fields.fields().field(path, "value");
        rejected(fields, RequestType.select);

        var order = new SelectWrapper<>().table("samples").orderByAsc(path);
        rejected(order, RequestType.select);

        var insert = new InsertWrapper<>().table("samples");
        insert.fields().field(path, "Alice");
        rejected(insert, RequestType.insert);

        var update = new UpdateWrapper<>().table("samples");
        update.fields().field(path, "Alice");
        rejected(update, RequestType.update);
        var increment = new UpdateWrapper<>().table("samples");
        increment.fields().inc(path, 1);
        rejected(increment, RequestType.update);
        var updateWhere = new UpdateWrapper<>().table("samples");
        updateWhere.fields().field("status", 1);
        updateWhere.where().eq(path, "Alice");
        rejected(updateWhere, RequestType.update);

        var delete = new DeleteWrapper<>().table("samples");
        delete.where().eq(path, "Alice");
        rejected(delete, RequestType.delete);

        var group = new GroupWrapper<>().table("samples");
        group.aggregations().field(path, "key");
        group.metrics().count("id", "total");
        rejected(group, RequestType.group);
        var metric = new GroupWrapper<>().table("samples");
        metric.metrics().sum(path, "total");
        rejected(metric, RequestType.group);
        var having = new GroupWrapper<>().table("samples");
        having.metrics().count("id", "total");
        having.having(h -> h.eq(path, "Alice"));
        rejected(having, RequestType.group);
        assertTrue(AliasUtil.ok(), "Failed HAVING must still restore alias context");
    }

    @Test
    void noNestedOrNamedPathWrapperApiRemains() {
        assertFalse(Arrays.stream(IConditionWrapper.class.getMethods()).anyMatch(m -> m.getName().equals("nested")));
        for (Class<?> type : List.of(ConditionManager.class, AsFieldListWrapper.class, InsertFieldsWrapper.class,
                UpdateFieldsWrapper.class, AggregationsWrapper.class, MetricsWrapper.class)) {
            assertFalse(Arrays.stream(type.getMethods()).anyMatch(m -> m.getName().equals("nested")));
            assertThrows(NoSuchMethodException.class, () -> type.getConstructor(String.class));
            assertThrows(NoSuchMethodException.class, () -> type.getMethod("getName"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"com.yulinlin.data.core.node.base.Nested",
            "com.yulinlin.data.core.wrapper.IConditionNestedWrapper", "com.yulinlin.data.core.wrapper.IChildrenWrapper",
            "com.yulinlin.jdbc.sql.parse.SqlJsonUtil", "com.yulinlin.jdbc.sql.parse.base.NestedParse"})
    void obsoleteTypesAreNotPackaged(String type) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
    }

    @Test
    void booleanCompositionAndColumnMappingRemainOrdinarySql() {
        var select = new SelectWrapper<>().table("samples");
        select.fields().field("displayName");
        select.where().eq("displayName", "Alice").and(a -> a.gt("amount", 3).lt("amount", 9))
                .not(n -> n.eq("status", 0));
        var sql = sql(select, RequestType.select);
        assertTrue(sql.getSql().contains("display_name"));
        assertTrue(sql.getSql().contains("and"));
        assertTrue(sql.getSql().contains("not"));
        assertEquals(List.of("Alice", 3, 9, 0), sql.getList());
    }

    @Test
    void wholeJsonColumnAndArithmeticStillUseTheirOriginalCoders() {
        var update = new UpdateWrapper<>().table("samples");
        update.fields().field("payload", Map.of("name", "Alice"))
                .inc("amount", new BigDecimal("1.25")).dec("status", 1);
        update.where().eq("id", 7L);
        var sql = sql(update, RequestType.update);
        assertEquals(List.of("{\"name\":\"Alice\"}", new BigDecimal("1.25"), 1, 7L), sql.getList());
        assertTrue(sql.getSql().contains("payload ="));
        assertFalse(sql.getSql().toLowerCase().contains("jsonb_set"));
    }

    @Test
    void rawSqlKeepsNativeOperatorsAndParameterBinding() {
        var command = CommandNode.of("SELECT payload->>'name' FROM samples WHERE payload->>'name' = #{name}",
                Map.of("name", "x' OR 1=1 --"), ParseType.select);
        var sql = sql(command, RequestType.select);
        assertTrue(sql.getSql().contains("payload->>'name'"));
        assertFalse(sql.getSql().contains("OR 1=1"));
        assertEquals(List.of("x' OR 1=1 --"), sql.getList());
    }

    @Test
    void explicitExpressionAndSqlMetadataRemainEscapeHatches() {
        var select = new SelectWrapper<>().table("samples");
        select.fields().meta(SqlKeys.name, "payload->>'name'").field("payload", "name");
        select.where().expression("payload->>'name' = #{name}", Map.of("name", "Alice"));
        var sql = sql(select, RequestType.select);
        assertTrue(sql.getSql().contains("payload->>'name'"));
        assertEquals(List.of("Alice"), sql.getList());
    }

    private static void rejected(INode node, RequestType type) {
        var error = assertThrows(UnsupportedOperationException.class, () -> sql(node, type));
        assertTrue(error.getMessage().contains("explicit parameterized SQL"));
    }

    private static SqlNode sql(INode node, RequestType type) {
        var context = new SimpParamsContext(type, Map.of(), new JdbcCoderManager().createEncoderBuffer(), Row.class, true);
        return (SqlNode) ((ParseResult) new SqlParseManager().parse(node, context)).getRequest();
    }

    public static class Row {
        @JoinField(name = "display_name") public String displayName;
        public Map<String, Object> payload;
        public Long id;
        public BigDecimal amount;
        public Integer status;
    }
}
