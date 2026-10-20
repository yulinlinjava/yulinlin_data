package com.yulinlin.sync;

/**
 * 有界 FIFO 存储扩展。offer 必须原子检查节点容量，peek 不移除，成功后才 acknowledge。
 * 相同规则的调用由 SyncManager 串行协调，不同规则可并发。
 * 持久化实现需另外定义快照编码与重启恢复；替换此接口不等于具备源事务 Outbox。
 */
public interface SyncTaskStore {
    boolean offer(String rule, SyncTask task, int capacity);
    SyncTask peek(String rule);
    void acknowledge(String rule, String taskId);
    int pendingTasks(String rule);
    int pendingNodes(String rule);
}
