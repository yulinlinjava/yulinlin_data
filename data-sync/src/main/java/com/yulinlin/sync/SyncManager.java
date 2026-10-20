package com.yulinlin.sync;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.transaction.TransactionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 每条规则一个顺序消费者；不同规则可并发，失败不越过头任务。 */
public final class SyncManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SyncManager.class);
    private static final class Channel {
        final SyncRule rule;
        final EntityPattern matcher;
        boolean running;
        boolean paused;
        boolean requiresRepair;
        long completed;
        long rejected;
        String failedTaskId;
        String lastError;
        boolean manual;
        boolean manualConflict;
        Channel(SyncRule rule) {
            this.rule = rule;
            matcher = new EntityPattern(rule.entities());
        }
    }
    private final Map<String, Channel> channels;
    private final SyncTaskStore store;
    private final SyncWriter writer;
    private final int capacity;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean started;
    private volatile boolean closed;

    public SyncManager(DataSyncProperties properties, SyncTaskStore store, SyncWriter writer) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = Objects.requireNonNull(writer, "writer");
        capacity = properties.getCapacity();
        if (capacity <= 0) throw new IllegalArgumentException("yulinlin.sync.capacity 必须为正数");
        if (properties.getRules() == null)
            throw new IllegalArgumentException("同步 rules 不能为 null，可用空列表仅启用人工同步");
        Map<String, Channel> definitions = new LinkedHashMap<>();
        Set<String> pairs = new HashSet<>();
        for (DataSyncProperties.Rule config : properties.getRules()) {
            if (config == null) throw new IllegalArgumentException("同步 rules 不能包含空规则");
            SyncRule rule = new SyncRule(config.getName(), config.getSource(), config.getTarget(),
                    config.getEntities());
            if (definitions.putIfAbsent(rule.name(), new Channel(rule)) != null
                    || !pairs.add(rule.source() + "\0" + rule.target()))
                throw new IllegalArgumentException("同步规则名称或源/目标重复：" + rule.name());
        }
        channels = Collections.unmodifiableMap(definitions);
    }

    /** 所有 Session 注册后校验组并启动增量消费；不会扫描实体或自动执行全量。 */
    public synchronized void start(Set<String> groups) {
        if (closed) throw new IllegalStateException("同步模块已关闭");
        if (started) return;
        for (Channel channel : channels.values()) {
            SyncRule rule = channel.rule;
            if (!groups.contains(rule.source()) || !groups.contains(rule.target()))
                throw new IllegalArgumentException("同步数据源未注册：" + rule.name()
                        + "；请检查 source/target 是否为已注册的 group");
        }
        started = true;
        for (Channel channel : channels.values()) synchronized (channel) { schedule(channel); }
    }

    /** 写后调用时仍在源事务内；0 行影响也重放，不能据此推断两库状态相同。 */
    public void capture(String source, ExecuteRequest<?> request) {
        if (request.getFromClass() == null || request.getFromClass() == Object.class
                || request.getWrappers().isEmpty()) return;
        // 整段自定义 SQL 无法跨方言翻译，即使调用方手动设置 fromClass 也不能重放。
        if (request.getWrappers().stream().anyMatch(com.yulinlin.data.core.node.CommandNode.class::isInstance)) {
            log.warn("自定义 SQL 不参与数据源同步，源组={}", source);
            return;
        }
        for (Channel channel : channels.values()) {
            if (!channel.rule.source().equals(source) || !channel.matcher.matches(request.getFromClass())) continue;
            synchronized (channel) {
                if (channel.manual) {
                    channel.manualConflict = true;
                    pause(channel, "人工全量同步期间检测到源写入，请停止源业务写入并重新核对数据", null, true);
                }
            }
            try {
                if (request.getWrappers().size() > capacity)
                    throw new IllegalStateException("单个同步请求节点数超过 capacity=" + capacity);
                SyncTask task = new SyncTask(channel.rule, request);
                TransactionUtil.afterCommit(() -> enqueue(channel, task));
            } catch (RuntimeException error) {
                // 延迟队列的 after 异常可能被隔离，必须在本模块显式保存同步缺口状态。
                synchronized (channel) {
                    channel.rejected++;
                    pause(channel, "生成同步快照或登记提交回调失败", error, true);
                }
            }
        }
    }

    private void enqueue(Channel channel, SyncTask task) {
        synchronized (channel) {
            try {
                if (channel.manual) {
                    channel.manualConflict = true;
                    pause(channel, "全量同步期间源事务提交，请停止源写入并重新核对数据", null, true);
                }
                if (closed || !store.offer(channel.rule.name(), task, capacity)) {
                    channel.rejected++;
                    pause(channel, closed ? "同步模块已关闭，源事务已提交" : "同步队列已满，源事务已提交",
                            null, true);
                    return;
                }
                schedule(channel);
            } catch (RuntimeException error) {
                channel.rejected++;
                pause(channel, "提交后同步任务存储失败，源事务已提交", error, true);
            }
        }
    }

    private void schedule(Channel channel) {
        if (!started || closed || channel.paused || channel.running || channel.manual) return;
        try {
            if (store.peek(channel.rule.name()) == null) return;
            channel.running = true;
            workers.execute(() -> drain(channel));
        } catch (RuntimeException error) {
            channel.running = false;
            pause(channel, "同步消费者启动失败", error, false);
        }
    }

    private void drain(Channel channel) {
        SyncTask task = null;
        try {
            while (true) {
                synchronized (channel) {
                    if (closed || channel.paused) { channel.running = false; return; }
                    task = store.peek(channel.rule.name());
                    if (task == null) { channel.running = false; return; }
                }
                writer.write(task);
                synchronized (channel) {
                    store.acknowledge(channel.rule.name(), task.id());
                    channel.completed++;
                }
            }
        } catch (Throwable error) {
            synchronized (channel) {
                channel.running = false;
                channel.failedTaskId = task == null ? null : task.id();
                pause(channel, "目标同步失败或确认失败，已暂停；请核对目标是否已提交，禁止盲目重试", error, false);
            }
            if (error instanceof Error fatal) throw fatal;
        }
    }

    private void pause(Channel channel, String reason, Throwable error, boolean gap) {
        channel.paused = true;
        channel.requiresRepair |= gap;
        channel.lastError = reason + (error == null ? "" : "：" + error.getMessage());
        log.error("数据源同步暂停，规则={}，存在同步缺口={}，原因={}",
                channel.rule.name(), channel.requiresRepair, channel.lastError, error);
    }

    /**
     * 人工核对并修复后恢复。discardFailed=true 仅移除失败头任务（例如目标已提交）。
     * false 会重试头任务；调用方必须确认 inc/dec 等不会重复执行。
     */
    public void resumeAfterRepair(String rule, boolean discardFailed) {
        Channel channel = required(rule);
        synchronized (channel) {
            if (closed || !started || channel.running || channel.manual || !channel.paused)
                throw new IllegalStateException("只能恢复已启动且消费者停止的暂停规则：" + rule);
            if (discardFailed) {
                if (channel.failedTaskId == null) throw new IllegalStateException("没有可丢弃的失败头任务：" + rule);
                store.acknowledge(rule, channel.failedTaskId);
            }
            channel.failedTaskId = null;
            channel.requiresRepair = false;
            channel.lastError = null;
            channel.paused = false;
            log.warn("人工确认修复后恢复同步，规则={}，丢弃失败任务={}", rule, discardFailed);
            schedule(channel);
        }
    }

    public List<SyncStatus> statuses() {
        return channels.values().stream().map(channel -> {
            synchronized (channel) {
                return new SyncStatus(channel.rule.name(), channel.rule.source(), channel.rule.target(),
                        closed ? "CLOSED" : channel.paused ? "PAUSED" : channel.manual ? "FULL_SYNC" : started ? "RUNNING" : "STARTING",
                        store.pendingTasks(channel.rule.name()), store.pendingNodes(channel.rule.name()),
                        channel.completed, channel.rejected, channel.requiresRepair,
                        channel.failedTaskId, channel.lastError);
            }
        }).toList();
    }

    private Channel required(String rule) {
        Channel channel = channels.get(rule);
        if (channel == null) throw new IllegalArgumentException("同步规则不存在：" + rule);
        return channel;
    }

    /** 人工维护期间排他占用写向该目标组的增量规则；不代替业务侧停写。 */
    public synchronized ManualGuard beginManual(String target) {
        if (closed) throw new IllegalStateException("同步模块已关闭");
        List<Channel> selected = channels.values().stream()
                .filter(channel -> channel.rule.target().equals(target)).toList();
        List<Channel> acquired = new ArrayList<>();
        try {
            for (Channel channel : selected) synchronized (channel) {
                if (channel.manual || channel.running || channel.paused
                        || store.pendingTasks(channel.rule.name()) != 0)
                    throw new IllegalStateException("请先排空并修复增量规则，再执行全量：" + channel.rule.name());
                channel.manual = true;
                channel.manualConflict = false;
                acquired.add(channel);
            }
            return new ManualGuard(acquired);
        } catch (RuntimeException error) {
            for (Channel channel : acquired) synchronized (channel) { channel.manual = false; schedule(channel); }
            throw error;
        }
    }

    public final class ManualGuard implements AutoCloseable {
        private final List<Channel> selected;
        private boolean released;
        private ManualGuard(List<Channel> selected) { this.selected = List.copyOf(selected); }
        public boolean conflicted() {
            for (Channel channel : selected) synchronized (channel) {
                if (channel.manualConflict) return true;
            }
            return false;
        }
        public void check() {
            if (closed) throw new IllegalStateException("同步模块正在关闭，请稍后续传");
            if (conflicted())
                throw new IllegalStateException("全量同步期间发生源写入，无法保证一致性；请停写并重新全量");
        }
        @Override public void close() {
            synchronized (SyncManager.this) {
                if (released) return;
                released = true;
                for (Channel channel : selected) synchronized (channel) {
                    channel.manual = false;
                    schedule(channel);
                }
            }
        }
    }

    @Override public synchronized void close() {
        closed = true;
        workers.shutdown(); // 不强行中断正在提交的目标事务；未开始的内存任务不能承诺保留。
        for (Channel channel : channels.values()) synchronized (channel) {
            int pending = store.pendingTasks(channel.rule.name());
            if (pending > 0) log.warn("同步模块关闭，规则={}，仍有任务={}；内存实现重启后不会恢复",
                    channel.rule.name(), pending);
        }
    }
}
