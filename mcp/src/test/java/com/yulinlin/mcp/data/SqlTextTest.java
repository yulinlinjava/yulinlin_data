package com.yulinlin.mcp.data;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SqlTextTest {
    @Test
    void renamesPlaceholdersAndExpandsLists() {
        var bound = SqlText.bind("select * from t where a = #{a} and id in (#{ids}) and b = #{a}",
                Map.of("a", "x", "ids", List.of(1L, 2L, 3L)));
        assertEquals("select * from t where a = #{p0} and id in (#{p1}, #{p2}, #{p3}) and b = #{p4}", bound.sql());
        assertEquals(List.of("x", 1L, 2L, 3L, "x"), new ArrayList<>(bound.params().values()));
    }

    @Test
    void keepsLiteralsAndAllowsNull() {
        var params = new HashMap<String, Object>();
        params.put("v", null);
        var bound = SqlText.bind("select 'a''b', \"c\" from t -- note\n where v is not distinct from #{v}", params);
        assertTrue(bound.sql().endsWith("#{p0}"));
        assertTrue(bound.params().containsKey("p0"));
        assertNull(bound.params().get("p0"));
    }

    @Test
    void rejectsUnsafeOrInconsistentInput() {
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select * from ${t}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select '#{a}'", Map.of("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select 1 /* #{a} */", Map.of("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select #{a}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select 1", Map.of("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select #{a}", Map.of("a", List.of())));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select #{a}", Map.of("a", Map.of("x", 1))));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select #{a-b}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> SqlText.bind("select #{a", Map.of()));
    }
}
