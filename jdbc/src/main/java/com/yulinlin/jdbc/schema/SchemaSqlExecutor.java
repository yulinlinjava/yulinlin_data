package com.yulinlin.jdbc.schema;

import java.sql.SQLException;

/** Session-owned callback used by schema validators when a DDL statement must be executed. */
@FunctionalInterface
public interface SchemaSqlExecutor {
    void execute(String sql) throws SQLException;
}
