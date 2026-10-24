package com.yulinlin.jdbc.sql;

import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlGroupExpressionContextTest {
    @Test
    void recordsGroupingExpressionsWithoutChangingSelectedExpressions() {
        var context = context();
        context.groupExpression("key", "some_expression");
        assertEquals("some_expression", context.groupExpression("key"));
        assertEquals("some_expression", context.selectExpression("key"));
        context.selectExpression("total", "sum(amount)");
        assertNull(context.groupExpression("total"));
        assertEquals("sum(amount)", context.selectExpression("total"));
        context.selectExpression("key", "count(amount)");
        assertNull(context.groupExpression("key"));
        assertEquals("count(amount)", context.selectExpression("key"));
        assertNull(context().groupExpression("key"));
    }

    @Test
    void sharedParserStillUsesAliasesForDialectsThatSupportThem() {
        var context = context();
        var wrapper = new GroupWrapper<>();
        wrapper.table("samples");
        wrapper.aggregations().field("category", "key");
        wrapper.metrics().sum("amount", "total");
        wrapper.having(h -> h.eq("key", "A").gt("total", 10));
        SqlNode node = (SqlNode) ((ParseResult) context.parseManager().parse(wrapper, context)).getRequest();
        assertEquals("category", context.groupExpression("key"));
        assertEquals("sum(amount)", context.selectExpression("total"));
        assertNull(context.groupExpression("total"));
        assertTrue(node.getSql().contains("group by key"));
        assertTrue(node.getSql().contains("having key ="));
        assertTrue(node.getSql().contains("total >"));
        assertFalse(node.getSql().contains("MIN("));
    }

    private static SqlParamsContext context() {
        return new SqlParamsContext(new SimpParamsContext(RequestType.group, Map.of(),
                new JdbcCoderManager().createEncoderBuffer(), Object.class, true), new SqlParseManager());
    }
}
