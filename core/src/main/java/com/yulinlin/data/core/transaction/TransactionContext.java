package com.yulinlin.data.core.transaction;

import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.proxy.EntityProxyFactory.ProxyChanges;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import lombok.SneakyThrows;

import java.util.*;

/** RouteSession 的内部事务状态；只保存数据，不独立决定事务生命周期。 */
public final class TransactionContext {
    private static final ThreadLocal<TransactionContext> CURRENT = new ThreadLocal<>();
    public static TransactionContext current() { return CURRENT.get(); }
    public static void bind(TransactionContext context) { CURRENT.set(context); }
    public static void unbind(TransactionContext context) { if (CURRENT.get() == context) CURRENT.remove(); }
    public final RouteSession route;
    private final TransactionLog transactionLog;
    public boolean rollbackOnly;
    public boolean closing;
    public boolean beforeCommitDone;
    public boolean commitAttempted;
    public boolean committed;
    public boolean completed;
    public Throwable failure;
    /**
     * 已登记到本事务的会话，按第一次访问的顺序排列，每个数据源组一个 master 节点。
     * 提交按此顺序依次进行，回滚按相反顺序；不是两阶段提交，见 doc/relations/transactions.md。
     * 不支持事务的会话（Elasticsearch 等）不登记，请求直接执行。
     */
    public final List<EntitySession> sessions = new ArrayList<>();
    public final Deque<Frame> frames = new ArrayDeque<>();
    private ProxyChanges proxyChanges;

    public static final class Frame {
        /** 本层开始时各会话的保存点；外层（第一层）不需要保存点。 */
        public final Map<EntitySession, Object> savepoints = new IdentityHashMap<>();
        public ProxyChanges.Snapshot proxySnapshot;
        public boolean rollbackOnly;
        /** 本层设置了超时时的截止时间（System.nanoTime）；timed 为 false 表示本层未设置。 */
        public boolean timed;
        public long deadlineNanos;
        public final Map<QueryCache, CacheInvalidation> invalidations = new IdentityHashMap<>();
        public final List<Runnable> afterCommitTasks = new ArrayList<>();
        public void merge(Frame child) {
            child.invalidations.forEach((cache, changes) -> invalidations
                    .computeIfAbsent(cache, ignored -> new CacheInvalidation()).merge(changes));
            afterCommitTasks.addAll(child.afterCommitTasks);
        }
    }

    public TransactionContext(RouteSession route, boolean logging) {
        this.route = route;
        transactionLog = TransactionLog.start(logging);
        frames.addLast(new Frame());
    }

    public TransactionLog transactionLog() { return transactionLog; }

    public int depth() { return frames.size(); }

    /**
     * 距离事务超时的剩余毫秒数，取所有活动层中最早的截止时间；没有任何一层设置超时时返回 {@link Long#MAX_VALUE}。
     * 内层设置的较短超时只在内层范围内生效，内层结束后恢复外层的截止时间。
     */
    public long remainingMillis() {
        boolean timed = false;
        long earliest = 0;
        for (Frame frame : frames) {
            if (!frame.timed) continue;
            if (!timed || frame.deadlineNanos - earliest < 0) earliest = frame.deadlineNanos;
            timed = true;
        }
        if (!timed) return Long.MAX_VALUE;
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(earliest - System.nanoTime());
    }
    public Frame frame() { return frames.getLast(); }

    /** 本事务中该数据源组已登记的会话；没有时返回 null。 */
    public EntitySession session(String group) {
        for (EntitySession s : sessions) if (java.util.Objects.equals(s.group(), group)) return s;
        return null;
    }
    public ProxyChanges proxyChanges() { return proxyChanges; }
    public void proxyChanges(ProxyChanges changes) {
        Objects.requireNonNull(changes, "changes");
        if (proxyChanges != null && proxyChanges != changes)
            throw new IllegalStateException("同一事务不能替换已有的代理修改上下文");
        proxyChanges = changes;
    }

    public boolean allowsChanges() {
        return current() == this && !completed
                && !beforeCommitDone;
    }

    public void fail(Throwable error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
    }

    public void attempt(Runnable action) {
        try { action.run(); }
        catch (Throwable error) { fail(error); }
    }

    @SneakyThrows
    public void throwIfFailed() { if (failure != null) throw failure; }

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

        public void merge(CacheInvalidation other) {
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

        public void apply(QueryCache cache) {
            if (all) cache.invalidateAll();
            else {
                cache.invalidate(Set.copyOf(namespaces));
                cache.invalidatePartitionTables(Set.copyOf(partitionTables));
                cache.invalidatePartitions(Set.copyOf(partitions));
            }
        }
    }
}
