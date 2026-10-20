package com.yulinlin.sync;

import com.yulinlin.data.core.request.ExecuteRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 一次写请求的独立快照；每次重放再次复制，失败执行不能污染保留的任务。 */
public final class SyncTask {
    private final String id = UUID.randomUUID().toString();
    private final Instant createdAt = Instant.now();
    private final SyncRule rule;
    private final ExecuteRequest<?> snapshot;

    public SyncTask(SyncRule rule, ExecuteRequest<?> request) {
        this.rule = Objects.requireNonNull(rule, "rule");
        this.snapshot = SyncSnapshots.copy(request);
    }

    public String id() { return id; }
    public Instant createdAt() { return createdAt; }
    public SyncRule rule() { return rule; }
    public int nodes() { return snapshot.getWrappers().size(); }
    public ExecuteRequest<?> newRequest() { return SyncSnapshots.copy(snapshot); }
}
