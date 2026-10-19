package com.yulinlin.data.core.transaction;

import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.proxy.EntityProxyFactory.ProxyChanges;
import com.yulinlin.data.core.session.EntitySession;
import lombok.SneakyThrows;

import java.util.*;

/** 当前线程的一次业务事务；所有 Session 共享，连接资源仍由各 Session 持有。 */
public final class TransactionContext {
    final TransactionListenerManager manager;
    private final TransactionLog transactionLog;
    int depth = 1;
    boolean rollbackOnly;
    boolean closing;
    boolean beforeCommitDone;
    boolean commitAttempted;
    boolean committed;
    boolean completed;
    boolean notified;
    Throwable failure;
    final Set<EntitySession> participants = new LinkedHashSet<>();
    final Map<Object, Map<String, EntitySession>> selections = new IdentityHashMap<>();
    final Map<QueryCache, CacheInvalidation> invalidations = new IdentityHashMap<>();
    final List<Runnable> afterCommitTasks = new ArrayList<>();
    private ProxyChanges proxyChanges;

    TransactionContext(TransactionListenerManager manager) {
        this.manager = manager;
        transactionLog = TransactionLog.start(manager.isTransactionLog());
    }

    public TransactionLog transactionLog() { return transactionLog; }

    public int depth() { return depth; }
    public TransactionListenerManager manager() { return manager; }
    public ProxyChanges proxyChanges() { return proxyChanges; }
    public void proxyChanges(ProxyChanges changes) {
        Objects.requireNonNull(changes, "changes");
        if (proxyChanges != null && proxyChanges != changes)
            throw new IllegalStateException("同一事务不能替换已有的代理修改上下文");
        proxyChanges = changes;
    }

    public boolean allowsChanges() {
        return TransactionListenerManager.currentContext() == this && !completed
                && !beforeCommitDone;
    }

    void fail(Throwable error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
    }

    void attempt(Runnable action) {
        try { action.run(); }
        catch (Throwable error) { fail(error); }
    }

    @SneakyThrows
    void throwIfFailed() { if (failure != null) throw failure; }

    /** 按 Provider 合并本事务的失效范围，不重复保存同一张表或分区。 */
    public static final class CacheInvalidation {
        public boolean all;
        public final Set<CacheNamespace> namespaces = new LinkedHashSet<>();
        public final Set<CachePartition> partitions = new LinkedHashSet<>();
        public final Set<CacheNamespace> partitionTables = new LinkedHashSet<>();

        public void addPartition(CachePartition partition) {
            if (!partitionTables.contains(partition.namespace())) partitions.add(partition);
        }

        public void addPartitionTable(CacheNamespace namespace) {
            partitionTables.add(namespace);
            partitions.removeIf(partition -> partition.namespace().equals(namespace));
        }

        void merge(CacheInvalidation other) {
            all |= other.all;
            if (all) {
                namespaces.clear();
                partitions.clear();
                partitionTables.clear();
            } else {
                namespaces.addAll(other.namespaces);
                other.partitionTables.forEach(this::addPartitionTable);
                other.partitions.forEach(this::addPartition);
            }
        }

        public boolean empty() { return !all && namespaces.isEmpty(); }

        void apply(QueryCache cache) {
            if (all) cache.invalidateAll();
            else {
                cache.invalidate(Set.copyOf(namespaces));
                cache.invalidatePartitionTables(Set.copyOf(partitionTables));
                cache.invalidatePartitions(Set.copyOf(partitions));
            }
        }
    }
}
