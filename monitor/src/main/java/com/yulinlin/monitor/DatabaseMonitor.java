package com.yulinlin.monitor;

import java.util.List;

/** Public API for reading SQL optimization summaries and bounded slow-operation details. */
public interface DatabaseMonitor {
    void record(DatabaseOperation operation);
    List<DatabaseOperation> recent(int limit);
    List<DatabaseSummary> summary();
    DatabaseMonitorSnapshot snapshot(int limit);
    void clear();
}
