package com.yulinlin.data.core.queue;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.DataProperties;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.lang.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local delayed write queue. It stores completed request nodes and delegates actual execution back to
 * {@code RouteSession}, so every database implementation keeps using its existing parser, transaction and batch path.
 */
@Slf4j
public final class WriteQueueManager implements AutoCloseable {

    private enum FlushTrigger {
        TIMER("timer"),
        THRESHOLD("threshold"),
        MANUAL("manual"),
        CLOSE("close");

        private final String label;

        FlushTrigger(String label) {
            this.label = label;
        }
    }

    @FunctionalInterface
    public interface Executor {
        int execute(EntitySession session, ExecuteRequest<?> request);
    }

    private record QueuedWrite(EntitySession session, ExecuteRequest<?> request,
                               int attempts, boolean mergeable) {
        int size() {
            return request.getWrappers().size();
        }

        QueuedWrite retry() {
            // Split a failed merged batch on its next attempt so one bad request does not discard its neighbours.
            return new QueuedWrite(session, request, attempts + 1, false);
        }
    }

    /** Session uses identity semantics: two independently created sessions must never share one write batch. */
    private record GroupKey(EntitySession session, RequestType requestType, Class<?> fromClass) {
        @Override
        public boolean equals(Object value) {
            return value instanceof GroupKey other
                    && session == other.session
                    && requestType == other.requestType
                    && fromClass == other.fromClass;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(session);
            result = 31 * result + System.identityHashCode(requestType);
            return 31 * result + System.identityHashCode(fromClass);
        }
    }

    private static final class Batch {
        private final EntitySession session;
        private final ExecuteRequest<?> request;
        private final List<QueuedWrite> writes = new ArrayList<>();
        private int size;

        private Batch(QueuedWrite write) {
            session = write.session();
            request = write.request().queuedSnapshot();
            writes.add(write);
            size = write.size();
        }

        private void add(QueuedWrite write) {
            request.mergeQueued(write.request());
            writes.add(write);
            size += write.size();
        }

        private int size() {
            return size;
        }

    }

    private static final class SessionLane {
        private final EntitySession session;
        private final List<Batch> batches = new ArrayList<>();

        private SessionLane(EntitySession session) {
            this.session = session;
        }
    }

    private record LaneResult(int affected, Throwable failure) {
    }

    private final int retryCount;
    private final int flushSize;
    private final int concurrency;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicReference<ConcurrentLinkedQueue<QueuedWrite>> active =
            new AtomicReference<>(new ConcurrentLinkedQueue<>());
    private final AtomicReference<Executor> executor = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean immediateFlushRunning = new AtomicBoolean();
    private final AtomicBoolean immediateFlushRequested = new AtomicBoolean();
    private final Object queueLock = new Object();
    /** Number of nodes in {@link #active}; guarded by {@link #queueLock}. */
    private int activeSize;
    private final ScheduledFuture<?> schedule;

    public WriteQueueManager(DataProperties.QueueProperties properties) {
        Objects.requireNonNull(properties, "properties");
        retryCount = properties.getRetryCount();
        flushSize = properties.getFlushSize();
        concurrency = properties.getConcurrency();
        schedule = ThreadUtil.scheduleVirtual(
                () -> flush(FlushTrigger.TIMER), properties.getFlushInterval().toMillis());
    }

    public void setExecutor(Executor value) {
        Objects.requireNonNull(value, "executor");
        Executor current = executor.get();
        if (current == value) return;
        if (!executor.compareAndSet(null, value)) {
            throw new IllegalStateException("Write queue executor has already been configured");
        }
    }

    /** Returns zero after the request nodes have been accepted by the in-memory queue. */
    public int submit(EntitySession session, ExecuteRequest<?> request) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        if (closed.get()) throw new IllegalStateException("Write queue is closed");
        if (executor.get() == null) throw new IllegalStateException("Write queue executor is not configured");

        ExecuteRequest<?> snapshot = request.queuedSnapshot();
        int size = snapshot.getWrappers().size();
        if (size == 0) return 0;
        pending.addAndGet(size);
        boolean accepted = false;
        boolean flushNow = false;
        try {
            synchronized (queueLock) {
                if (closed.get()) throw new IllegalStateException("Write queue is closed");
                QueuedWrite write = new QueuedWrite(session, snapshot, 0, true);
                active.get().add(write);
                activeSize += write.size();
                flushNow = activeSize >= flushSize;
                accepted = true;
            }
        } finally {
            if (!accepted) pending.addAndGet(-size);
        }
        if (flushNow) requestImmediateFlush();
        return 0;
    }

    public int pending() {
        return pending.get();
    }

    /** Flushes one atomic queue snapshot. New submissions are left for the next flush. */
    public int flush() {
        return flush(FlushTrigger.MANUAL);
    }

    private synchronized int flush(FlushTrigger trigger) {
        ConcurrentLinkedQueue<QueuedWrite> snapshot;
        synchronized (queueLock) {
            snapshot = active.getAndSet(new ConcurrentLinkedQueue<>());
            activeSize = 0;
        }
        if (snapshot.isEmpty()) return 0;

        Executor target = executor.get();
        if (target == null) {
            prepend(snapshot);
            throw new IllegalStateException("Write queue executor is not configured");
        }

        long started = System.nanoTime();
        List<Batch> batches = groupedBatches(snapshot);
        int nodes = 0;
        for (Batch batch : batches) nodes += batch.size();
        List<SessionLane> lanes = sessionLanes(batches);
        int affected = executeLanes(target, lanes);
        logFlushSummary(trigger, lanes.size(), batches.size(), nodes, affected,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), pending.get());
        return affected;
    }

    private void logFlushSummary(FlushTrigger trigger, int sessions, int groups, int nodes,
                                 int affected, long elapsedMs, int remaining) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("QUEUE FLUSH trigger={} sessions={} groups={} nodes={} affected={} elapsedMs={} pending={}",
                        trigger.label, sessions, groups, nodes, affected, elapsedMs, remaining);
            }
        } catch (RuntimeException ignored) {
            // Observability must never change delayed-write execution semantics.
        }
    }

    /** Globally groups one flush snapshot; queued writes intentionally provide no cross-group ordering guarantee. */
    private List<Batch> groupedBatches(ConcurrentLinkedQueue<QueuedWrite> writes) {
        List<Batch> result = new ArrayList<>();
        LinkedHashMap<GroupKey, Batch> grouped = new LinkedHashMap<>();
        QueuedWrite write;
        while ((write = writes.poll()) != null) {
            if (!write.mergeable()) {
                result.add(new Batch(write));
                continue;
            }
            ExecuteRequest<?> request = write.request();
            GroupKey key = groupKey(write);
            Batch batch = grouped.get(key);
            if (batch == null) {
                batch = new Batch(write);
                grouped.put(key, batch);
                result.add(batch);
            } else batch.add(write);
        }
        return result;
    }

    private List<SessionLane> sessionLanes(List<Batch> batches) {
        IdentityHashMap<EntitySession, SessionLane> bySession = new IdentityHashMap<>();
        List<SessionLane> result = new ArrayList<>();
        for (Batch batch : batches) {
            SessionLane lane = bySession.get(batch.session);
            if (lane == null) {
                lane = new SessionLane(batch.session);
                bySession.put(batch.session, lane);
                result.add(lane);
            }
            lane.batches.add(batch);
        }
        return result;
    }

    private int executeLanes(Executor target, List<SessionLane> lanes) {
        ConcurrentLinkedQueue<SessionLane> waiting = new ConcurrentLinkedQueue<>(lanes);
        int workerCount = Math.min(concurrency, lanes.size());
        List<Future<LaneResult>> futures = new ArrayList<>(workerCount);
        for (int index = 0; index < workerCount; index++) {
            futures.add(ThreadUtil.submitVirtual(() -> executeWorker(target, waiting)));
        }

        int affected = 0;
        Throwable failure = null;
        boolean interrupted = false;
        for (Future<LaneResult> future : futures) {
            while (true) {
                try {
                    LaneResult result = future.get();
                    affected += result.affected();
                    if (result.failure() != null) failure = appendFailure(failure, result.failure());
                    break;
                } catch (InterruptedException error) {
                    interrupted = true;
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    failure = appendFailure(failure, cause);
                    break;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        if (failure != null) throw new IllegalStateException(failure);
        return affected;
    }

    private LaneResult executeWorker(Executor target, ConcurrentLinkedQueue<SessionLane> waiting) {
        int affected = 0;
        Throwable failure = null;
        SessionLane lane;
        while ((lane = waiting.poll()) != null) {
            LaneResult result = executeLane(target, lane);
            affected += result.affected();
            if (result.failure() != null) failure = appendFailure(failure, result.failure());
        }
        return new LaneResult(affected, failure);
    }

    private LaneResult executeLane(Executor target, SessionLane lane) {
        int affected = 0;
        for (int index = 0; index < lane.batches.size(); index++) {
            Batch batch = lane.batches.get(index);
            try {
                affected += target.execute(lane.session, batch.request);
                pending.addAndGet(-batch.size());
            } catch (Exception error) {
                retry(batch, error);
            } catch (Error error) {
                restoreBatches(lane.batches, index);
                return new LaneResult(affected, error);
            }
        }
        return new LaneResult(affected, null);
    }

    private static Throwable appendFailure(Throwable current, Throwable next) {
        if (current == null) return next;
        if (current != next) current.addSuppressed(next);
        return current;
    }

    private void retry(Batch batch, Throwable error) {
        List<QueuedWrite> waiting = new ArrayList<>();
        int retried = 0;
        int discarded = 0;
        for (QueuedWrite write : batch.writes) {
            if (write.attempts() < retryCount && !closed.get()) {
                waiting.add(write.retry());
                retried += write.size();
            } else {
                discarded += write.size();
                pending.addAndGet(-write.size());
            }
        }
        prepend(waiting);
        if (discarded > 0) {
            log.error("Delayed write failed permanently; discarded {} node(s) after {} retry attempt(s)",
                    discarded, retryCount, error);
        } else {
            log.warn("Delayed write failed; {} node(s) will be retried on the next flush", retried, error);
        }
    }

    private void restoreBatches(List<Batch> batches, int startIndex) {
        List<QueuedWrite> waiting = new ArrayList<>();
        for (int index = startIndex; index < batches.size(); index++) {
            waiting.addAll(batches.get(index).writes);
        }
        prepend(waiting);
    }

    private void prepend(Iterable<QueuedWrite> waiting) {
        synchronized (queueLock) {
            ConcurrentLinkedQueue<QueuedWrite> current = active.get();
            ConcurrentLinkedQueue<QueuedWrite> reordered = new ConcurrentLinkedQueue<>();
            int restoredSize = 0;
            for (QueuedWrite write : waiting) {
                reordered.add(write);
                restoredSize += write.size();
            }
            QueuedWrite write;
            while ((write = current.poll()) != null) reordered.add(write);
            active.set(reordered);
            activeSize += restoredSize;
        }
    }

    private static GroupKey groupKey(QueuedWrite write) {
        ExecuteRequest<?> request = write.request();
        return new GroupKey(write.session(), request.getRequestType(), request.getFromClass());
    }

    private void requestImmediateFlush() {
        if (closed.get()) return;
        immediateFlushRequested.set(true);
        startImmediateFlush();
    }

    private void startImmediateFlush() {
        if (closed.get() || !immediateFlushRunning.compareAndSet(false, true)) return;
        try {
            ThreadUtil.submitVirtual(() -> {
                try {
                    while (!closed.get() && immediateFlushRequested.getAndSet(false)) {
                        try {
                            flush(FlushTrigger.THRESHOLD);
                        } catch (Throwable error) {
                            log.error("Immediate delayed-write flush failed", error);
                        }
                    }
                } finally {
                    immediateFlushRunning.set(false);
                    if (!closed.get() && immediateFlushRequested.get()) startImmediateFlush();
                }
            });
        } catch (RuntimeException error) {
            immediateFlushRunning.set(false);
            log.error("Unable to schedule immediate delayed-write flush; periodic flush remains active", error);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        schedule.cancel(false);
        flush(FlushTrigger.CLOSE);
        if (pending.get() > 0) {
            log.error("Write queue closed with {} node(s) that could not be flushed", pending.get());
        }
    }
}
