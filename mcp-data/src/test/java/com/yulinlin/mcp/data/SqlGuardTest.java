package com.yulinlin.mcp.data;

import org.junit.jupiter.api.Test;

import static com.yulinlin.mcp.data.SqlGuard.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlGuardTest {
    @Test
    void classifiesQueriesAndWrites() {
        assertEquals(QUERY, SqlGuard.check("select * from t where id = #{id}", false));
        assertEquals(QUERY, SqlGuard.check("  (select 1);", false));
        assertEquals(QUERY, SqlGuard.check("with a as (select replace(name, 'x', 'y') n, update_time from t) select * from a", false));
        assertEquals(WRITE, SqlGuard.check("update t set a = 1 where id = 2", true));
        assertEquals(WRITE, SqlGuard.check("insert into t(a) values (1)", true));
        assertEquals(WRITE, SqlGuard.check("with x as (select 1) delete from t where id in (select * from x)", true));
    }

    @Test
    void rejectsDangerousStatements() {
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.check("drop table t", true));
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.check("select 1; delete from t", true));
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.check("delete from t", true));
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.check("select sleep(10)", false));
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.check("select * from t for update", false));
        assertEquals(QUERY, SqlGuard.check("select * from t for update", true));
        var e = assertThrows(IllegalArgumentException.class, () -> SqlGuard.check("select * from ${table}", false));
        assertTrue(e.getMessage().contains("${"));
    }

    @Test
    void ignoresKeywordsInsideStringsAndComments() {
        assertEquals(QUERY, SqlGuard.check("select 'delete from t; drop table x' as s -- drop table t\n from t", false));
        assertEquals(QUERY, SqlGuard.check("select /* ; insert into t */ 1", false));
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.check("select 'unterminated", false));
    }
}
