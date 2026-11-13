package com.yulinlin.security.risk;

import com.yulinlin.data.core.transaction.TransactionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.*;
import com.yulinlin.security.risk.RiskModels.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Default detection is in-memory; business control is synchronous; history writes are asynchronous. */
@Slf4j
public final class DefaultRiskService implements RiskService, AutoCloseable {
    private final RiskProperties properties;
    private final BehaviorStore store;
    private final RiskControl control;
    private final Clock clock;
    private final Map<String, RiskDetector> detectors;
    private final Map<String, String> signatures = new HashMap<>();
    private final ConcurrentMap<Subject, SubjectState> subjects = new ConcurrentHashMap<>();
    private final Object admission = new Object();
    private final ArrayBlockingQueue<Event> events;
    private final Cache<String, Cached> dedup;
    private final ScheduledExecutorService worker, cleaner;
    private final AtomicBoolean started = new AtomicBoolean(), closed = new AtomicBoolean(), flushScheduled = new AtomicBoolean();
    private final LongAdder dropped = new LongAdder(), failures = new LongAdder(), capacity = new LongAdder();
    private volatile boolean recovering;
    private long nextSnapshot;
    private volatile List<Event> pending = List.of();
    private int pendingFailures;
    private long retryAt;

    public DefaultRiskService(RiskProperties p, BehaviorStore store, List<RiskDetector> custom, RiskControl control) {
        this(p, store, custom, control, Clock.systemUTC());
    }
    public DefaultRiskService(RiskProperties p, BehaviorStore store, List<RiskDetector> custom, RiskControl control, Clock clock) {
        this(p, Objects.requireNonNull(store), custom, control, clock, null);
    }
    static DefaultRiskService forReplay(RiskProperties p, List<RiskDetector> custom, Clock clock, Ticker ticker) {
        return new DefaultRiskService(p, null, custom, null, clock, Objects.requireNonNull(ticker));
    }
    private DefaultRiskService(RiskProperties p, BehaviorStore store, List<RiskDetector> custom,
                               RiskControl control, Clock clock, Ticker ticker) {
        properties = new ObjectMapper().findAndRegisterModules().convertValue(p, RiskProperties.class);
        properties.validate();
        this.store = store; this.control = control; this.clock = Objects.requireNonNull(clock);
        if (store != null && properties.requiresControl() && control == null)
            throw new IllegalArgumentException("启用 BLOCK 必须提供业务 RiskControl Bean");
        Map<String, RiskDetector> registered = new LinkedHashMap<>();
        for (RiskDetector d : List.of(new FastSwitchDetector(properties), new ActiveAccessDetector(properties))) registered.put(d.id(), d);
        for (var d : custom) {
            String id = Objects.requireNonNull(d.id(), "组件 ID");
            if (!id.matches("[a-z][a-z0-9-]{0,63}") || registered.putIfAbsent(id, d) != null)
                throw new IllegalArgumentException("无效或重复 RiskDetector ID: " + id);
        }
        detectors = Map.copyOf(registered);
        properties.getBehaviors().forEach((name, b) -> {
            for (String id : b.getComponents())
                if (!detectors.containsKey(id)) throw new IllegalArgumentException("未知 RiskDetector: " + id);
            signatures.put(name, hash("components-v2:" + b + ":" + properties.getMemory().getMaximumEventsPerSubject()));
        });
        events = new ArrayBlockingQueue<>(store == null ? 1 : properties.getAsync().getQueueCapacity());
        var cache = Caffeine.newBuilder().maximumSize(properties.getAsync().getQueueCapacity())
                .expireAfterWrite(properties.getMemory().getRetention());
        if (ticker != null) cache.ticker(ticker).executor(Runnable::run);
        dedup = cache.build();
        if (store == null) { worker = null; cleaner = null; started.set(true); return; }
        worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true)
                .name("security-risk-store").inheritInheritableThreadLocals(false).factory());
        cleaner = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true)
                .name("security-risk-cleanup").inheritInheritableThreadLocals(false).factory());
    }
    /** Recovery completes before accepting checks; component snapshots are never merged with live traffic. */
    public synchronized void start() {
        if (started.get()) return;
        if (closed.get()) throw new IllegalStateException("风控服务已关闭");
        try { store.initialize(); }
        catch (Exception error) {
            closed.set(true); worker.shutdownNow(); cleaner.shutdownNow();
            throw new IllegalStateException("风控存储初始化失败", error);
        }
        recovering = properties.getMemory().isRestoreOnStartup();
        try {
            if (recovering) store.restore(clock.millis() - retention(), properties.getMemory().getMaximumSize(),
                    value -> restore(value, clock.millis()));
        } catch (Exception error) { failures.increment(); log.error("风控恢复失败，未恢复对象重新观察", error); }
        finally { recovering = false; }
        started.set(true);
        long period = Math.min(properties.getAsync().getFlushInterval().toMillis(), properties.getAsync().getSnapshotInterval().toMillis());
        worker.scheduleWithFixedDelay(this::tick, period, period, TimeUnit.MILLISECONDS);
        long cleanup = properties.getStorage().getCleanupInterval().toMillis();
        cleaner.scheduleWithFixedDelay(() -> {
            try { store.cleanup(clock.millis() - properties.getStorage().getRetention().toMillis(), clock.millis()); }
            catch (Exception error) { failures.increment(); log.error("风控历史清理失败", error); }
        }, cleanup, cleanup, TimeUnit.MILLISECONDS);
    }
    @Override public Decision checkAndRecord(BehaviorEvent event) {
        Objects.requireNonNull(event, "event");
        if (!started.get() || closed.get()) throw new IllegalStateException("风控服务未启动或正在关闭");
        if (store != null && (TransactionContext.current() != null || TransactionSynchronizationManager.isActualTransactionActive()))
            throw new IllegalStateException("请在业务事务开始前调用风控");
        var b = behavior(event.behaviorType());
        long now = clock.millis();
        if (!b.isEnabled()) return findRestriction(event.subject()).map(r -> blocked(r, List.of()))
                .orElseGet(() -> allowed(Status.DISABLED, List.of()));
        SubjectState s = acquire(event.subject(), now);
        if (s == null) {
            // Statistics capacity must never bypass an existing business restriction.
            var restriction = findRestriction(event.subject()); capacity.increment();
            Decision result = restriction.map(r -> blocked(r, List.of())).orElseGet(() -> allowed(Status.CAPACITY, List.of()));
            enqueue(event, result, now); return result;
        }
        synchronized (s) {
            if (s.retired) return checkAndRecord(event);
            now = Math.max(now, s.touchedAt); s.touchedAt = now; s.version++;
            var restriction = findRestriction(event.subject());
            if (restriction.isPresent()) {
                Restriction current = restriction.get();
                if (s.restriction == null || !s.restriction.id().equals(current.id())) resetDetectors(s);
                s.restriction = current;
                Decision result = blocked(current, s.components); enqueue(event, result, now); return result;
            }
            if (s.restriction != null) reset(s);
            if (s.exemptUntil > now) {
                Decision result = allowed(Status.EXEMPT, List.of()); enqueue(event, result, now); return result;
            }
            if (s.exemptUntil != 0) reset(s);
            String key = event.eventId() == null ? null : hash(event.userId() + "\0" + event.behaviorType() + "\0" + event.eventId());
            Cached cached = key == null ? null : dedup.getIfPresent(key);
            if (cached != null && !cached.generation.equals(s.generation)) cached = null;
            if (cached != null && cached.complete) return cached.decision;
            if (cached == null) {
                var results = new ArrayList<Detection>();
                for (String id : b.getComponents()) {
                    var result = Objects.requireNonNull(detectors.get(id).detect(event, now), "组件不能返回 null");
                    if (!id.equals(result.componentId())) throw new IllegalStateException("组件结果 ID 不匹配: " + id);
                    results.add(result);
                }
                cached = new Cached(s.generation, evaluate(List.copyOf(results)));
                // Retry a failed business action without counting a stable event ID twice.
                if (key != null) dedup.put(key, cached);
            }
            s.components = cached.decision.components();
            List<Match> blockHits = s.components.stream()
                    .filter(d -> properties.mode(event.behaviorType(), d.componentId()) == RiskProperties.Mode.BLOCK)
                    .flatMap(d -> d.matches().stream()).toList();
            Decision result = cached.decision;
            if (store != null && control != null && !blockHits.isEmpty()) {
                var outcome = Objects.requireNonNull(control.onRisk(event.subject(), blockHits), "RiskControl.onRisk 不能返回 null");
                if (outcome.isPresent()) {
                    s.restriction = outcome.get(); resetDetectors(s);
                    result = blocked(outcome.get(), s.components);
                }
            }
            cached.decision = result; cached.complete = true;
            enqueue(event, result, now); return result;
        }
    }
    private Optional<Restriction> findRestriction(Subject subject) {
        return control == null ? Optional.empty()
                : Objects.requireNonNull(control.findRestriction(subject), "RiskControl.findRestriction 不能返回 null");
    }
    private Decision evaluate(List<Detection> results) {
        Status status = results.stream().anyMatch(d -> d.status() == DetectionStatus.MATCHED) ? Status.SUSPECTED
                : results.stream().anyMatch(d -> d.status() == DetectionStatus.CAPACITY) ? Status.CAPACITY
                : results.stream().anyMatch(d -> d.status() == DetectionStatus.OBSERVING) ? Status.OBSERVING : Status.ALLOWED;
        return allowed(status, results);
    }
    private static Decision allowed(Status status, List<Detection> results) {
        return new Decision(true, status, 0, hits(results), results, null);
    }
    private static Decision blocked(Restriction restriction, List<Detection> results) {
        return new Decision(false, Status.BLOCKED, restriction.expiresAt() == null ? 0 : restriction.expiresAt(),
                hits(results), results, restriction);
    }
    private static List<Match> hits(List<Detection> results) { return results.stream().flatMap(d -> d.matches().stream()).toList(); }
    private void enqueue(BehaviorEvent request, Decision result, long now) {
        if (store == null) return;
        String id = request.eventId() == null ? UUID.randomUUID().toString()
                : hash(request.userId() + "\0" + request.behaviorType() + "\0" + request.eventId());
        if (!events.offer(new Event(id, request.subject(), request.resourceId(), now, result))) dropped.increment();
        if (events.size() >= properties.getAsync().getBatchSize() && flushScheduled.compareAndSet(false, true)) {
            try { worker.execute(() -> { try { flushEvents(false); } finally { flushScheduled.set(false); } }); }
            catch (RejectedExecutionException ignored) { flushScheduled.set(false); }
        }
    }
    private SubjectState acquire(Subject subject, long now) {
        SubjectState existing = subjects.get(subject);
        if (existing != null) return existing;
        synchronized (admission) {
            existing = subjects.get(subject);
            if (existing != null) return existing;
            if (subjects.size() >= properties.getMemory().getMaximumSize()) return null;
            var s = new SubjectState(subject, signatures.get(subject.behaviorType()), now);
            subjects.put(subject, s); return s;
        }
    }
    private RiskProperties.Behavior behavior(String type) {
        var b = properties.getBehaviors().get(type);
        if (b == null) throw new IllegalArgumentException("未配置风控行为：" + type);
        return b;
    }
    private void resetDetectors(SubjectState s) {
        for (String id : behavior(s.subject.behaviorType()).getComponents()) detectors.get(id).reset(s.subject);
        s.generation = UUID.randomUUID().toString();
    }
    private void reset(SubjectState s) {
        resetDetectors(s); s.restriction = null; s.exemptUntil = 0; s.components = List.of();
    }
    private State freeze(SubjectState s) {
        Map<String, String> snapshots = new HashMap<>();
        for (String id : behavior(s.subject.behaviorType()).getComponents()) {
            if (detectors.get(id) instanceof PersistedRiskDetector persistent) {
                String payload = persistent.snapshot(s.subject);
                if (payload != null) snapshots.put(id, payload);
            }
        }
        return new State(s.subject, s.streamId, s.signature, s.version, s.touchedAt, s.exemptUntil, s.components, s.restriction, snapshots);
    }
    private void restore(Restored value, long now) {
        State saved = value.state();
        if (!Objects.equals(saved.signature(), signatures.get(saved.subject().behaviorType())) || saved.updatedAt() > now) return;
        SubjectState s = acquire(saved.subject(), now);
        if (s == null) return;
        try {
            synchronized (s) {
                for (var entry : saved.detectorStates().entrySet())
                    if (detectors.get(entry.getKey()) instanceof PersistedRiskDetector d) d.restore(saved.subject(), entry.getValue(), now);
                s.components = saved.components(); s.restriction = saved.restriction(); s.exemptUntil = saved.exemptUntil(); s.version++;
            }
        } catch (RuntimeException error) {
            reset(s); subjects.remove(s.subject, s);
            failures.increment(); log.warn("主体快照不兼容，重新观察: {}", s.subject, error);
        }
    }
    private void tick() {
        try {
            flushEvents(false);
            long now = clock.millis();
            if (now >= nextSnapshot) { nextSnapshot = now + properties.getAsync().getSnapshotInterval().toMillis(); flushSnapshots(); }
            subjects.forEach((key, s) -> {
                synchronized (s) {
                    if (s.touchedAt < now - retention() && s.exemptUntil <= now && s.persistedVersion >= s.version) {
                        evictDetectors(s); s.retired = true; subjects.remove(key, s);
                    }
                }
            });
        } catch (Exception error) { failures.increment(); log.error("风控后台维护失败", error); }
    }
    private void flushEvents(boolean shuttingDown) {
        int budget = events.size() + pending.size();
        while (budget > 0) {
            if (pending.isEmpty()) {
                List<Event> batch = new ArrayList<>();
                events.drainTo(batch, properties.getAsync().getBatchSize());
                pending = List.copyOf(batch);
                pendingFailures = 0; retryAt = 0;
            }
            if (pending.isEmpty() || (!shuttingDown && clock.millis() < retryAt)) return;
            int count = pending.size();
            try {
                store.writeEvents(pending);
                pending = List.of();
            } catch (Exception error) {
                failures.increment();
                pendingFailures++;
                if (pendingFailures > properties.getAsync().getRetryCount()) {
                    dropped.add(count); pending = List.of();
                    log.error("风控明细重试耗尽，丢弃 {} 条；内存统计保留", count, error);
                } else {
                    retryAt = clock.millis() + properties.getAsync().getRetryInterval().toMillis();
                    log.warn("风控明细写入失败，等待重试，条数={}", count, error);
                    if (!shuttingDown) return;
                    continue;
                }
            }
            budget -= count;
        }
    }
    private void flushSnapshots() {
        for (SubjectState s : subjects.values()) {
            Snapshot snapshot;
            synchronized (s) {
                if (s.version <= s.persistedVersion || clock.millis() < s.retryAt) continue;
                snapshot = new Snapshot(freeze(s));
            }
            try {
                store.writeSnapshot(snapshot);
                synchronized (s) { s.persistedVersion = Math.max(s.persistedVersion, snapshot.state().version()); s.retryAt = 0; }
            } catch (Exception error) {
                failures.increment();
                synchronized (s) { s.retryAt = clock.millis() + properties.getAsync().getRetryInterval().toMillis(); }
                log.warn("风控快照写入失败，稍后重试", error);
            }
        }
    }
    @Override public Optional<View> inspect(Subject subject) {
        var s = subjects.get(subject);
        if (s == null) return Optional.empty();
        synchronized (s) { return Optional.of(new View(freeze(s))); }
    }
    @Override public List<View> listRisks(int offset, int limit) {
        page(offset, limit);
        return subjects.keySet().stream().map(this::inspect).flatMap(Optional::stream)
                .filter(v -> v.state().restriction() != null || !hits(v.state().components()).isEmpty())
                .sorted(Comparator.comparing(v -> v.state().subject().userId() + "\0" + v.state().subject().behaviorType()))
                .skip(offset).limit(limit).toList();
    }
    @Override public void release(Subject subject) {
        behavior(subject.behaviorType());
        var s = acquire(subject, clock.millis());
        if (s == null) { if (control != null) control.release(subject); return; }
        synchronized (s) {
            if (s.retired) { release(subject); return; }
            if (control != null) control.release(subject);
            reset(s); s.touchedAt = clock.millis(); s.version++;
        }
    }
    @Override public void exempt(Subject subject, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("豁免时长必须为正");
        behavior(subject.behaviorType());
        var s = acquire(subject, clock.millis());
        if (s == null) throw new IllegalStateException("风控内存容量已满");
        synchronized (s) {
            if (s.retired) { exempt(subject, duration); return; }
            reset(s); s.exemptUntil = Math.addExact(clock.millis(), duration.toMillis());
            s.touchedAt = clock.millis(); s.version++;
        }
    }
    @Override public List<Event> history(Subject subject, long from, long to, int offset, int limit) {
        if (store == null) throw new IllegalStateException("回放不提供数据库历史查询");
        page(offset, limit);
        if (to <= from) throw new IllegalArgumentException("to 必须大于 from");
        try { return store.history(subject, from, to, offset, limit); }
        catch (Exception error) { throw new IllegalStateException("行为历史查询失败", error); }
    }
    @Override public Metrics metrics() {
        return new Metrics(subjects.size(), events.size() + pending.size(), dropped.sum(), failures.sum(), capacity.sum(), recovering);
    }
    public static void page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 1000) throw new IllegalArgumentException("offset >= 0，limit 必须为 1..1000");
    }
    /** Test/administration barrier; never called from normal checkAndRecord. */
    public CompletableFuture<Void> flush() {
        if (worker == null) return CompletableFuture.completedFuture(null);
        var done = new CompletableFuture<Void>();
        worker.execute(() -> {
            try { flushEvents(false); flushSnapshots(); done.complete(null); }
            catch (Throwable error) { done.completeExceptionally(error); }
        });
        return done;
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (worker == null) { clearDetectors(); return; }
        cleaner.shutdownNow();
        if (started.get()) worker.execute(() -> { flushEvents(true); flushSnapshots(); });
        worker.shutdown();
        try {
            if (!worker.awaitTermination(properties.getAsync().getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                worker.shutdownNow();
                log.warn("风控停机刷新超时，可能存在未落库数据");
            }
        } catch (InterruptedException error) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
        clearDetectors();
    }
    private long retention() { return properties.getMemory().getRetention().toMillis(); }
    private void clearDetectors() {
        subjects.values().forEach(s -> { synchronized (s) { evictDetectors(s); } }); subjects.clear();
    }
    private void evictDetectors(SubjectState s) {
        for (String id : behavior(s.subject.behaviorType()).getComponents()) detectors.get(id).evict(s.subject);
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static final class Cached {
        final String generation; Decision decision; boolean complete;
        Cached(String generation, Decision decision) { this.generation = generation; this.decision = decision; }
    }
    private static final class SubjectState {
        final Subject subject;
        final String streamId = UUID.randomUUID().toString(), signature;
        String generation = UUID.randomUUID().toString();
        long version, persistedVersion, touchedAt, exemptUntil, retryAt;
        boolean retired;
        List<Detection> components = List.of();
        Restriction restriction;
        SubjectState(Subject subject, String signature, long now) { this.subject = subject; this.signature = signature; touchedAt = now; }
    }
}
