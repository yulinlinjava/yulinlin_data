package com.yulinlin.monitor;

/** Aggregation for one physical SQL template, used as an optimization ranking entry. */
public record DatabaseSummary(
        String group,
        String session,
        String operation,
        String entity,
        String source,
        String table,
        String sql,
        String priority,
        long executionCount,
        long slowCount,
        long errorCount,
        double slowRate,
        long averageElapsedMs,
        long maxElapsedMs,
        long lastElapsedMs,
        long lastExecutedAt
) {
}
