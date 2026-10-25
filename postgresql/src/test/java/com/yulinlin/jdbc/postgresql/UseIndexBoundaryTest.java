package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.SqlNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UseIndexBoundaryTest {
    @Test
    void ignoredHintsPreserveSelectGroupPaginationCountAndBindings() {
        var select = new SelectWrapper<>().table("samples", "s").page(2, 5).orderByAsc("id");
        select.fields().field("id");
        select.where().eq("name", "O'Reilly");
        var group = new GroupWrapper<>().table("samples", "s").page(2, 5);
        group.aggregations().field("name", "name");
        group.metrics().sum("amount", "amount");
        group.where().eq("name", "O'Reilly");
        group.having().gt("amount", 2);
        var parser = new PostgresqlParseManager();
        for (var wrapper : List.of(select, group, new CountWrapper(select), new CountWrapper(group))) {
            var request = QueryRequest.newInstance(Map.class, wrapper).useIndex("missing_index");
            RequestType type = wrapper instanceof CountWrapper ? RequestType.count
                    : wrapper instanceof GroupWrapper ? RequestType.group : RequestType.select;
            var plain = (SqlNode) ((ParseResult) parser.parse(wrapper, context(type))).getRequest();
            var hinted = (SqlNode) ((ParseResult) parser.parse(wrapper, context(type), request.getIndexHint())).getRequest();
            assertEquals(plain.getSql(), hinted.getSql());
            assertEquals(plain.getList(), hinted.getList());
            assertTrue(hinted.getList().contains("O'Reilly"));
            assertFalse(hinted.getSql().contains("O'Reilly"));
            assertFalse(hinted.getSql().contains("missing_index"));
            assertFalse(hinted.getSql().contains("USE INDEX"));
        }
    }

    private static SimpParamsContext context(RequestType type) {
        return new SimpParamsContext(type, Map.of(),
                new JdbcCoderManager().createEncoderBuffer(), Object.class, true);
    }
}
