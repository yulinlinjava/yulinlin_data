package com.yulinlin.sync;

/** completedRows 是当前这一轮的累计复制行数，不是目标 INSERT/UPDATE 的驱动影响行数。 */
public record SyncResult(String taskId, String source, String target, String entity,
                         String status, String lastPrimaryKey, long completedRows,
                         long rowsThisRun, long batchesThisRun, long elapsedMillis) { }
