package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.request.TypedRow;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class H2TypedRowTest {
    private static final String SQL = "select \"id\", \"amount\", \"incomplete\", \"project_code\" from \"repro_minute\""
            + " where \"id\" = #{id}";

    @Test
    void typedRowKeepsNativeTypesNullColumnsAndOrder() throws Exception {
        try (var f = new Fixture("")) {
            TypedRow row = QueryRequest.newInstance(SQL, Map.of("id", 3L), TypedRow.class).cache(false).selectOne();
            assertEquals(List.of("id", "amount", "incomplete", "project_code"), new ArrayList<>(row.keySet()));
            assertEquals(3L, ((Number) row.get("id")).longValue());
            assertInstanceOf(Long.class, row.get("id"));
            assertTrue(row.containsKey("amount"));
            assertNull(row.get("amount"));
            assertNull(row.get("incomplete"));
            assertEquals("123", row.get("project_code"));

            List<TypedRow> each = new ArrayList<>();
            QueryRequest.newInstance(SQL, Map.of("id", 2L), TypedRow.class).cache(false).selectEach(each::add);
            assertEquals(1, each.size());
            assertInstanceOf(Double.class, each.get(0).get("amount"));
            assertInstanceOf(Integer.class, each.get(0).get("incomplete"));
        }
    }

    @Test
    void commandMaxRowsCapsRowsAndNullParametersBind() throws Exception {
        try (var f = new Fixture("")) {
            var command = new com.yulinlin.data.core.node.CommandNode<>(
                    "select \"id\" from \"repro_minute\" order by \"id\"", Map.of(),
                    com.yulinlin.data.core.parse.ParseType.select).maxRows(3);
            var rows = QueryRequest.newInstance(TypedRow.class, command).cache(false).selectList();
            assertEquals(3, rows.size());

            var params = new java.util.HashMap<String, Object>();
            params.put("amount", null);
            List<TypedRow> nullRows = QueryRequest.newInstance(
                    "select \"id\" from \"repro_minute\" where \"amount\" is not distinct from #{amount}", params, TypedRow.class)
                    .cache(false).selectList();
            assertEquals(1, nullRows.size());
            assertEquals(3L, ((Number) nullRows.get(0).get("id")).longValue());
        }
    }

    @Test
    void mapRowsAreUnchanged() throws Exception {
        try (var f = new Fixture("")) {
            Map<?, ?> row = QueryRequest.newInstance(SQL, Map.of("id", 3L), Map.class).cache(false).selectOne();
            assertFalse(row.containsKey("amount"), "Map.class keeps the legacy behaviour of dropping NULL columns");
            assertFalse(row instanceof TypedRow);
        }
    }
}
