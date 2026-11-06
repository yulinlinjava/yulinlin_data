package com.yulinlin.security.risk;

import com.yulinlin.data.core.transaction.TransactionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

/** No IO while holding a subject lock or on the checkAndRecord caller. */
@Slf4j
public final class DefaultRiskService implements RiskService, AutoCloseable {
    private static final long MINUTE = 60_000;
    private final RiskProperties properties;
    private final BehaviorStore store;
    private final Clock clock;
    private final Map<String, RiskRule> rules;
    private final Map<String, String> signatures = new HashMap<>();
    private final List<RiskActionHandler> handlers;
    private final ConcurrentMap<Subject, SubjectState> subjects = new ConcurrentHashMap<>();
    private final Object admission = new Object();
    private final ArrayBlockingQueue<Event> events;
    private final Cache<String, Decision> dedup;
    private final ScheduledExecutorService worker;
    private final ScheduledExecutorService cleaner;
    private final AtomicBoolean started = new AtomicBoolean(), closed = new AtomicBoolean(), flushScheduled = new AtomicBoolean();
    private final LongAdder dropped = new LongAdder(), failures = new LongAdder(), capacity = new LongAdder();
    private volatile boolean recovering;
    private long nextSnapshot;
    private volatile List<Event> pending = List.of();
    private int pendingFailures;
    private long retryAt;

    public DefaultRiskService(RiskProperties properties, BehaviorStore store, List<RiskRule> customRules,
                              List<RiskActionHandler> handlers) {
        this(properties, store, customRules, handlers, Clock.systemUTC());
    }
    public DefaultRiskService(RiskProperties properties, BehaviorStore store, List<RiskRule> customRules,
                              List<RiskActionHandler> handlers, Clock clock) {
        // Isolate runtime configuration from callers mutating a Properties bean.
        this.properties = new ObjectMapper().findAndRegisterModules().convertValue(properties, RiskProperties.class);
        this.properties.validate();
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.handlers = List.copyOf(handlers);
        Map<String, RiskRule> resolved = new HashMap<>();
        resolved.put("ACTIVE_MINUTES", RiskRule.activeMinutes());
        resolved.put("DENSE_ACCESS", RiskRule.denseAccess());
        Set<String> customTypes = new HashSet<>();
        for (RiskRule rule : customRules) {
            if (!customTypes.add(rule.type())) throw new IllegalArgumentException("重复 RiskRule: " + rule.type());
            resolved.put(rule.type(), rule);
        }
        this.rules = Map.copyOf(resolved);
        this.properties.getBehaviors().forEach((name, behavior) -> {
            StringBuilder fingerprint = new StringBuilder();
            for (var rule : behavior.getRules()) {
                if (!resolved.containsKey(rule.getType())) throw new IllegalArgumentException("未知 RiskRule: " + rule.getType());
                fingerprint.append(rule).append('\n');
            }
            signatures.put(name, hash(fingerprint.toString()));
        });
        events = new ArrayBlockingQueue<>(properties.getAsync().getQueueCapacity());
        dedup = Caffeine.newBuilder().maximumSize(properties.getAsync().getQueueCapacity())
                .expireAfterWrite(properties.getMemory().getRetention()).build();
        worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true)
                .name("security-risk-store").inheritInheritableThreadLocals(false).factory());
        cleaner = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true)
                .name("security-risk-cleanup").inheritInheritableThreadLocals(false).factory());
    }

    /** Initialize schema before serving traffic, then recover and persist on the worker only. */
    public void start() {
        if (!started.compareAndSet(false, true)) return;
        try { store.initialize(); }
        catch (Exception error) { started.set(false); worker.shutdownNow(); cleaner.shutdownNow(); throw new IllegalStateException("风控存储初始化失败", error); }
        recovering = properties.getMemory().isRestoreOnStartup();
        long startup = clock.millis();
        worker.execute(() -> {
            if (recovering) {
                try {
                    store.restore(startup - retention(), properties.getMemory().getMaximumSize(),
                            value -> restore(value, startup));
                } catch (Exception error) { failures.increment(); log.error("风控恢复失败，未恢复对象从新窗口开始观察", error); }
                finally { recovering = false; }
            }
        });
        long period = Math.min(properties.getAsync().getFlushInterval().toMillis(),
                Math.min(properties.getAsync().getSnapshotInterval().toMillis(), properties.getStorage().getCleanupInterval().toMillis()));
        worker.scheduleWithFixedDelay(this::tick, period, period, TimeUnit.MILLISECONDS);
        long cleanupInterval = properties.getStorage().getCleanupInterval().toMillis();
        cleaner.scheduleWithFixedDelay(() -> {
            try { store.cleanup(clock.millis() - properties.getStorage().getRetention().toMillis(), clock.millis()); }
            catch (Exception error) { failures.increment(); log.error("风控历史清理失败", error); }
        }, cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
    }

    @Override public Decision checkAndRecord(BehaviorEvent event) {
        Objects.requireNonNull(event, "event");
        if (!started.get() || closed.get()) throw new IllegalStateException("风控服务未启动或正在关闭");
        // A memory update survives rollback, but calling after business work has begun is still an integration error.
        if (TransactionContext.current() != null || TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("请在业务事务开始前调用风控");
        var behavior = behavior(event.behaviorType());
        if (!behavior.isEnabled()) return decision(true, Status.DISABLED, 0, List.of());
        long now = clock.millis();
        SubjectState state = acquire(event.subject(), now);
        if (state == null) {
            capacity.increment();
            Decision result = decision(true, Status.CAPACITY, 0, List.of());
            enqueue(event, result, now);
            return result;
        }
        Decision result;
        boolean notify;
        synchronized (state) {
            if (state.retired) return checkAndRecord(event);
            String eventKey = event.eventId() == null ? null : hash(event.userId() + "\0" + event.behaviorType() + "\0" + event.eventId());
            Decision previous = eventKey == null ? null : dedup.getIfPresent(eventKey);
            if (previous != null && previous.status() == Status.BLOCKED && previous.blockedUntil() <= now) {
                dedup.invalidate(eventKey);
                previous = null;
            }
            if (previous != null) {
                // A retry cannot replay an old ALLOW after another request has blocked the user.
                return state.blockedUntil > now && properties.getMode() == RiskProperties.Mode.BLOCK
                        ? decision(false, Status.BLOCKED, state.blockedUntil, state.matches) : previous;
            }
            now = Math.max(now, state.touchedAt); // Clock rollback must not produce negative interarrival gaps.
            state.touchedAt = now;
            state.version++;
            List<String> oldRules = state.matches.stream().map(Match::ruleId).toList();
            if (state.exemptUntil > now) {
                result = decision(true, Status.EXEMPT, 0, List.of());
            } else if (state.blockedUntil > now) {
                boolean blocked = properties.getMode() == RiskProperties.Mode.BLOCK;
                result = decision(!blocked, blocked ? Status.BLOCKED : Status.SUSPECTED, state.blockedUntil, state.matches);
            } else {
                if (state.blockedUntil != 0 || state.exemptUntil != 0) state.reset(now);
                add(state, now, behavior);
                long end = floor(now);
                if (state.evaluatedAt != end) {
                    state.evaluatedAt = end;
                    List<Match> matches = new ArrayList<>();
                    long maximumWindow = behavior.getRules().stream().filter(RiskProperties.Rule::isEnabled)
                            .mapToLong(r -> r.getWindow().toMillis()).max().orElse(MINUTE);
                    List<Minute> minutes = state.minutes.subMap(end - maximumWindow, true, end, false)
                            .values().stream().map(Bucket::freeze).toList();
                    for (var config : behavior.getRules()) {
                        if (config.isEnabled() && state.observedSince <= end - config.getWindow().toMillis())
                            rules.get(config.getType()).evaluate(config, end, minutes).ifPresent(matches::add);
                    }
                    state.matches = List.copyOf(matches);
                }
                if (!state.matches.isEmpty()) {
                    boolean blocked = properties.getMode() == RiskProperties.Mode.BLOCK;
                    if (blocked) state.blockedUntil = Math.addExact(now, properties.getBlockDuration().toMillis());
                    result = decision(!blocked, blocked ? Status.BLOCKED : Status.SUSPECTED, state.blockedUntil, state.matches);
                } else {
                    boolean ready = behavior.getRules().stream().filter(RiskProperties.Rule::isEnabled)
                            .anyMatch(r -> state.observedSince <= end - r.getWindow().toMillis());
                    result = decision(true, ready ? Status.ALLOWED : Status.OBSERVING, 0, List.of());
                }
            }
            notify = !result.matches().isEmpty() && !oldRules.equals(result.matches().stream().map(Match::ruleId).toList());
            if (eventKey != null) dedup.put(eventKey, result);
            enqueue(event, result, now);
        }
        if (notify) for (var handler : handlers) {
            try { handler.onRisk(event, result); }
            catch (RuntimeException error) { log.error("风控事件处理器失败；已有判断保持有效", error); }
        }
        return result;
    }

    private void add(SubjectState state, long now, RiskProperties.Behavior behavior) {
        long minute = floor(now);
        state.minutes.headMap(floor(now - retention()), false).clear();
        Bucket bucket = state.minutes.computeIfAbsent(minute, Bucket::new);
        bucket.requests++;
        if (state.lastAt >= 0 && state.lastAt >= state.resetAt) {
            boolean first = bucket.intervals == 0;
            if (first) bucket.firstPreviousAt = state.lastAt;
            bucket.intervals++;
            long gap = now - state.lastAt;
            for (var rule : behavior.getRules()) if (gap <= rule.getShortGap().toMillis()) {
                bucket.shortIntervals.merge(rule.getId(), 1L, Long::sum);
                if (first) bucket.shortIntervals.put("first:" + rule.getId(), 1L);
            }
        }
        state.lastAt = now;
        bucket.version = state.version;
    }

    private void enqueue(BehaviorEvent request, Decision decision, long now) {
        String id = request.eventId() == null ? UUID.randomUUID().toString()
                : hash(request.userId() + "\0" + request.behaviorType() + "\0" + request.eventId());
        if (!events.offer(new Event(id, request.subject(), request.resourceId(), now, decision))) dropped.increment();
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
            SubjectState created = new SubjectState(subject, signatures.get(subject.behaviorType()), now);
            subjects.put(subject, created);
            return created;
        }
    }
    private RiskProperties.Behavior behavior(String type) {
        var value = properties.getBehaviors().get(type);
        if (value == null) throw new IllegalArgumentException("未配置风控行为：" + type);
        return value;
    }
    private void restore(Restored value, long startup) {
        State saved = value.state();
        if (!Objects.equals(saved.signature(), signatures.get(saved.subject().behaviorType())) || saved.updatedAt() > startup) return;
        SubjectState target = acquire(saved.subject(), startup);
        if (target == null) return;
        synchronized (target) {
            if (target.managed) return; // A local release/exemption made during recovery wins.
            // Requests made before recovery finishes are retained. Replay only persisted, pre-startup data.
            target.observedSince = Math.min(target.observedSince, saved.observedSince());
            target.lastAt = Math.max(target.lastAt, saved.lastAt());
            target.blockedUntil = Math.max(target.blockedUntil, saved.blockedUntil());
            target.exemptUntil = Math.max(target.exemptUntil, saved.exemptUntil());
            target.resetAt = saved.resetAt();
            target.matches = saved.matches();
            target.evaluatedAt = Long.MIN_VALUE;
            target.version++;
            for (Minute minute : value.minutes()) {
                if (minute.start() < Math.max(floor(startup - retention()), floor(target.resetAt))) continue;
                Bucket bucket = target.minutes.computeIfAbsent(minute.start(), Bucket::new);
                bucket.requests += minute.requests();
                // Across recovery's live/persisted boundary an unobserved edge is deliberately not invented.
                boolean earlier = minute.intervals() > 0 && (bucket.intervals == 0 || minute.firstPreviousAt() < bucket.firstPreviousAt);
                if (earlier) {
                    bucket.firstPreviousAt = minute.firstPreviousAt();
                    bucket.shortIntervals.keySet().removeIf(k -> k.startsWith("first:"));
                }
                bucket.intervals += minute.intervals();
                minute.shortIntervals().forEach((key, count) -> {
                    if (key.startsWith("first:")) { if (earlier) bucket.shortIntervals.put(key, count); }
                    else bucket.shortIntervals.merge(key, count, Long::sum);
                });
                bucket.version = target.version;
            }
        }
    }

    private void tick() {
        try {
            flushEvents(false);
            long now = clock.millis();
            if (now >= nextSnapshot) {
                nextSnapshot = now + properties.getAsync().getSnapshotInterval().toMillis();
                flushSnapshots();
            }
            subjects.forEach((key, state) -> {
                    synchronized (state) {
                        if (state.touchedAt < now - retention() && state.blockedUntil <= now && state.exemptUntil <= now
                                && state.persistedVersion >= state.version) {
                            state.retired = true;
                            subjects.remove(key, state);
                        }
                    }
            });
        } catch (Exception error) { failures.increment(); log.error("风控后台维护失败；请求继续使用本机内存", error); }
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
        for (SubjectState state : subjects.values()) {
            Snapshot snapshot;
            synchronized (state) {
                if (state.version <= state.persistedVersion || clock.millis() < state.retryAt) continue;
                snapshot = new Snapshot(state.freeze(), state.minutes.values().stream()
                        .filter(b -> b.version > state.persistedVersion).map(Bucket::freeze).toList());
            }
            try {
                store.writeSnapshot(snapshot);
                synchronized (state) {
                    state.persistedVersion = Math.max(state.persistedVersion, snapshot.state().version());
                    state.retryAt = 0;
                }
            } catch (Exception error) {
                failures.increment();
                synchronized (state) { state.retryAt = clock.millis() + properties.getAsync().getRetryInterval().toMillis(); }
                log.warn("风控快照写入失败，保留脏状态，下次重试最新版本", error);
            }
        }
    }

    @Override public Optional<View> inspect(Subject subject) {
        SubjectState state = subjects.get(subject);
        if (state == null) return Optional.empty();
        synchronized (state) { return Optional.of(new View(state.freeze(), state.minutes.values().stream().map(Bucket::freeze).toList())); }
    }
    @Override public List<View> listRisks(int offset, int limit) {
        page(offset, limit);
        return subjects.keySet().stream().map(this::inspect).flatMap(Optional::stream)
                .filter(view -> !view.state().matches().isEmpty() || view.state().blockedUntil() > clock.millis())
                .sorted(Comparator.comparing(v -> v.state().subject().userId() + "\0" + v.state().subject().behaviorType()))
                .skip(offset).limit(limit).toList();
    }
    @Override public void release(Subject subject) { manage(subject, 0); }
    @Override public void exempt(Subject subject, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("豁免时长必须为正");
        manage(subject, Math.addExact(clock.millis(), duration.toMillis()));
    }
    private void manage(Subject subject, long exemptUntil) {
        behavior(subject.behaviorType());
        SubjectState state = acquire(subject, clock.millis());
        if (state == null) throw new IllegalStateException("风控内存容量已满");
        synchronized (state) {
            if (state.retired) { manage(subject, exemptUntil); return; }
            state.managed = true;
            state.reset(clock.millis()); state.exemptUntil = exemptUntil;
            state.touchedAt = clock.millis(); state.version++;
        }
        // Replays of earlier event IDs must not replay an obsolete decision after an administrative action.
        dedup.invalidateAll();
    }
    @Override public List<Event> history(Subject subject, long from, long to, int offset, int limit) {
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
        var done = new CompletableFuture<Void>();
        worker.execute(() -> {
            try { flushEvents(false); flushSnapshots(); done.complete(null); }
            catch (Throwable error) { done.completeExceptionally(error); }
        });
        return done;
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        cleaner.shutdownNow();
        if (started.get()) worker.execute(() -> { flushEvents(true); flushSnapshots(); });
        worker.shutdown();
        try {
            if (!worker.awaitTermination(properties.getAsync().getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                worker.shutdownNow();
                log.warn("风控停机刷新超时，可能存在未落库数据");
            }
        } catch (InterruptedException error) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
    }
    private long retention() { return properties.getMemory().getRetention().toMillis(); }
    private static long floor(long millis) { return Math.floorDiv(millis, MINUTE) * MINUTE; }
    private static Decision decision(boolean allowed, Status status, long until, List<Match> matches) { return new Decision(allowed, status, until, matches); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static final class Bucket {
        final long start;
        long requests, intervals, firstPreviousAt = -1, version;
        final Map<String, Long> shortIntervals = new HashMap<>();
        Bucket(long start) { this.start = start; }
        Minute freeze() { return new Minute(start, requests, intervals, firstPreviousAt, shortIntervals, version); }
    }
    private static final class SubjectState {
        final Subject subject;
        final String streamId = UUID.randomUUID().toString(), signature;
        long version, persistedVersion, observedSince, lastAt = -1, touchedAt, blockedUntil, exemptUntil, resetAt;
        long evaluatedAt = Long.MIN_VALUE, retryAt;
        boolean managed, retired;
        List<Match> matches = List.of();
        final NavigableMap<Long, Bucket> minutes = new TreeMap<>();
        SubjectState(Subject subject, String signature, long now) {
            this.subject = subject; this.signature = signature; observedSince = now; touchedAt = now; resetAt = now;
        }
        void reset(long now) {
            managed = true;
            blockedUntil = 0; exemptUntil = 0; matches = List.of(); minutes.clear();
            observedSince = now; lastAt = -1; resetAt = now; evaluatedAt = Long.MIN_VALUE;
        }
        State freeze() { return new State(subject, streamId, signature, version, observedSince, lastAt,
                blockedUntil, exemptUntil, resetAt, matches, touchedAt); }
    }
}

