package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fixture;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
class H2PlainCountTest {
    @ParameterizedTest
    @ValueSource(strings = {"", ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"})
    void countsAllRowsIncludingNullProjectionAndOmitsDisplayOptions(String mode) throws Exception {
        try (var f = new Fixture(mode)) {
            var select = new SelectWrapper<>().table("repro_minute").page(2, 1).orderByAsc("id").lock();
            select.fields().field("amount"); // One row has NULL amount; it must still be counted.
            var request = QueryRequest.newInstance(Map.class, select);
            assertEquals(10, f.session.count(request));
            assertEquals("select count(1) as total from \"repro_minute\"", f.lastSql().getSql());
            assertTrue(f.lastSql().getList().isEmpty());
            assertEquals(10, legacyCount(f.anchor, "from \"repro_minute\"", List.of()));

            select.where().eq("id", -1L);
            assertEquals(0, f.session.count(request));
            String sql = f.lastSql().getSql();
            assertTrue(sql.startsWith("select count(1) as total from \"repro_minute\" where "));
            assertFalse(sql.contains("order by"));
            assertFalse(sql.contains("limit"));
            assertFalse(sql.contains("for update"));
            assertEquals(List.of(-1L), f.lastSql().getList());
            assertEquals(0, legacyCount(f.anchor, "from \"repro_minute\" where \"id\" = ?", List.of(-1L)));
            assertEquals(0, f.session.count(request.copy(new CountWrapper(select))));
            assertEquals(sql, f.lastSql().getSql());
            assertFalse(f.session.explainCount(request).isEmpty());
            assertEquals("EXPLAIN " + sql, f.lastSql().getSql());
            assertEquals(List.of(-1L), f.lastSql().getList());
            assertEquals(2, select.getPageNumber());
            assertEquals(1, select.getPageSize());
            assertTrue(select.isLock(), "Count must not mutate the original list query");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "missing"})
    void leftJoinCountsDuplicateAndUnmatchedRowsWithBindingsInOrder(String project) throws Exception {
        try (var f = new Fixture("")) {
            var select = new SelectWrapper<>().table("repro_minute", "a").page(2, 1).orderByAsc("b.id");
            select.fields().field("b.amount");
            select.left("repro_minute", "b").eq("b.project_code", project);
            select.where().lte("a.id", 2L);
            var request = QueryRequest.newInstance(Map.class, select);
            int expected = project.equals("123") ? 18 : 2;
            assertEquals(expected, f.session.count(request));
            String sql = f.lastSql().getSql();
            assertTrue(sql.startsWith("select count(1) as total from \"repro_minute\" \"a\" left join "));
            assertFalse(sql.contains("order by"));
            assertFalse(sql.contains("limit"));
            assertEquals(List.of(project, 2L), f.lastSql().getList());
            assertEquals(expected, legacyCount(f.anchor,
                    "from \"repro_minute\" \"a\" left join \"repro_minute\" \"b\""
                            + " on \"b\".\"project_code\" = ? where \"a\".\"id\" <= ?", List.of(project, 2L)));
            assertFalse(f.session.explainCount(request).isEmpty());
            assertEquals("EXPLAIN " + sql, f.lastSql().getSql());
            assertEquals(List.of(project, 2L), f.lastSql().getList());
        }
    }

    private static int legacyCount(Connection connection, String fromAndWhere, List<?> parameters) throws Exception {
        try (var statement = connection.prepareStatement("select count(1) from (select 1 " + fromAndWhere + ") temp")) {
            for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }
}
