package com.yulinlin.monitor;

/** One retained slow or failed physical database operation. SQL parameters are intentionally excluded. */
public record DatabaseOperation(
        long id,
        long timestamp,
        String group,
        String session,
        String operation,
        String entity,
        String source,
        String table,
        String sql,
        long elapsedMs,
        int affectedRows,
        boolean success,
        String error
) {
}
