package com.yulinlin.sync;

/** 仅运行期状态，不是持久化同步历史；rejected 表示未保留的请求数。 */
public record SyncStatus(String rule, String source, String target, String state,
                         int pendingTasks, int pendingNodes, long completed, long rejected,
                         boolean requiresRepair, String failedTaskId, String lastError) { }
