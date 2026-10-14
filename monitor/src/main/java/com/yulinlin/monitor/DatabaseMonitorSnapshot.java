package com.yulinlin.monitor;

import java.util.List;

/** Immutable optimization ranking and recent slow-operation payload consumed by the built-in page. */
public record DatabaseMonitorSnapshot(
        long thresholdMs,
        int maxRecords,
        int retainedRecords,
        List<DatabaseSummary> summaries,
        List<DatabaseOperation> records
) {
}
