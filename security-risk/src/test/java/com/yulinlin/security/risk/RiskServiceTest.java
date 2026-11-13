package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class RiskServiceTest {
    static final Subject USER = new Subject("u", "video");
    static RiskProperties config() {
        var p = new RiskProperties(); p.setEnabled(true);
        p.getMemory().setRestoreOnStartup(false);
        p.getAsync().setFlushInterval(Duration.ofHours(1)); p.getAsync().setSnapshotInterval(Duration.ofHours(1));
        p.getAsync().setQueueCapacity(5000);
        p.getBehaviors().put("video", new RiskProperties.Behavior());
        return p;
    }
    static RiskProperties small() {
        var p = config(); p.getBehaviors().get("video").setComponents(List.of("fast-switch"));
        p.getBehaviors().get("video").getFastSwitch().getRapid().setMinVideos(2);
        return p;
    }
    static class Time extends Clock {
        final AtomicLong now = new AtomicLong();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        public long millis() { return now.get(); }
    }
    static class Store implements BehaviorStore {
        final Map<String, Event> events = new ConcurrentHashMap<>();
        final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
        final List<String> ioThreads = new CopyOnWriteArrayList<>();
        volatile int failEvents;
        public void initialize() {}
        public void writeEvents(List<Event> rows) {
            ioThreads.add(Thread.currentThread().getName());
            if (failEvents-- > 0) throw new IllegalStateException("simulated outage");
            rows.forEach(row -> events.putIfAbsent(row.id(), row));
        }
        public void writeSnapshot(Snapshot value) {
            ioThreads.add(Thread.currentThread().getName());
            snapshots.compute(value.state().streamId(), (id, old) -> old == null || old.state().version() < value.state().version() ? value : old);
        }
        public void restore(long cutoff, int limit, Consumer<Restored> consumer) throws Exception {
            var seen = new HashSet<Subject>();
            snapshots.values().stream().sorted(Comparator.comparingLong((Snapshot s) -> s.state().updatedAt()).reversed())
                    .filter(s -> s.state().updatedAt() >= cutoff && seen.add(s.state().subject())).limit(limit)
                    .forEach(s -> consumer.accept(new Restored(s.state())));
        }
        public List<Event> history(Subject s, long from, long to, int offset, int limit) { return List.copyOf(events.values()); }
        public void cleanup(long cutoff, long now) {}
    }
    static class Control implements RiskControl {
        final Time time; final Map<Subject, Restriction> restrictions = new ConcurrentHashMap<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile List<Match> lastHits;
        boolean fail, decline, permanent;
        Control(Time time) { this.time = time; }
        public Optional<Restriction> findRestriction(Subject subject) {
            var r = restrictions.get(subject);
            if (r != null && r.expiresAt() != null && r.expiresAt() <= time.millis()) { restrictions.remove(subject, r); r = null; }
            return Optional.ofNullable(r);
        }
        public Optional<Restriction> onRisk(Subject subject, List<Match> hits) {
            int call = calls.incrementAndGet(); lastHits = hits;
            if (fail) throw new IllegalStateException("business control unavailable");
            if (decline) return Optional.empty();
            var r = new Restriction("business-" + call, "业务决定限制", permanent ? null : time.millis() + 5000);
            restrictions.put(subject, r); return Optional.of(r);
        }
        public void release(Subject subject) { restrictions.remove(subject); }
    }
    static DefaultRiskService service(RiskProperties p, Store store, Time time) {
        return service(p, store, time, List.of(), null);
    }
    static DefaultRiskService service(RiskProperties p, Store store, Time time, List<RiskDetector> custom, RiskControl control) {
        var s = new DefaultRiskService(p, store, custom, control, time); s.start(); return s;
    }
    static Decision visit(DefaultRiskService service, Time time, long at) {
        time.now.set(at); return service.checkAndRecord(new BehaviorEvent("u", "video", "v" + at));
    }
    static class CountingDetector implements RiskDetector {
        final String id; final AtomicInteger calls = new AtomicInteger(), resets = new AtomicInteger();
        boolean hit;
        CountingDetector(String id, boolean hit) { this.id = id; this.hit = hit; }
        public String id() { return id; }
        public Detection detect(BehaviorEvent e, long now) {
            long count = calls.incrementAndGet(); var stats = Map.of("calls", count);
            return new Detection(id, hit ? DetectionStatus.MATCHED : DetectionStatus.NORMAL, null,
                    hit ? List.of(new Match(id, "business", now, now, "业务规则", stats)) : List.of(), stats);
        }
        public void reset(Subject s) { resets.incrementAndGet(); }
    }

    @Test void businessOwnsDurationAndExpiryAndReleaseResetObservation() {
        var p = small(); p.setMode(RiskProperties.Mode.BLOCK); var t = new Time(); var c = new Control(t);
        try (var s = service(p, new Store(), t, List.of(), c)) {
            assertTrue(visit(s,t,0).allowed()); assertTrue(visit(s,t,1000).allowed());
            var hit = visit(s,t,2000); assertFalse(hit.allowed()); assertEquals(7000,hit.blockedUntil());
            assertEquals(1,c.calls.get());
            assertFalse(visit(s,t,3000).allowed()); assertEquals(1,c.calls.get());
            assertTrue(visit(s,t,7000).allowed());
            visit(s,t,8000); assertFalse(visit(s,t,9000).allowed());
            s.release(USER); assertTrue(visit(s,t,9001).allowed()); assertTrue(c.restrictions.isEmpty());
        }
    }
    @Test void allComponentsEvaluateAndOnlyBlockingHitsReachOneControlCall() {
        var p = config(); p.setMode(RiskProperties.Mode.BLOCK);
        p.getBehaviors().get("video").setComponents(List.of("one", "two", "three"));
        p.getBehaviors().get("video").getComponentModes().put("two", RiskProperties.Mode.OBSERVE);
        var one=new CountingDetector("one",true);var two=new CountingDetector("two",true);var three=new CountingDetector("three",true);
        var t=new Time();var c=new Control(t);
        try(var s=service(p,new Store(),t,List.of(one,two,three),c)) {
            var result=visit(s,t,0);
            assertEquals(3,result.matches().size());assertEquals(1,c.calls.get());
            assertEquals(List.of("one","three"),c.lastHits.stream().map(Match::componentId).toList());
            assertEquals(1,one.calls.get());assertEquals(1,two.calls.get());assertEquals(1,three.calls.get());
            assertEquals(1,one.resets.get());
        }
    }
    @Test void observeNeverPunishesAndBusinessCanDeclineOrPermanentlyRestrict() {
        var p=small();var t=new Time();var c=new Control(t);
        try(var s=service(p,new Store(),t,List.of(),c)) {
            visit(s,t,0);visit(s,t,1000);
            assertEquals(Status.SUSPECTED,visit(s,t,2000).status());assertEquals(0,c.calls.get());
            c.restrictions.put(USER,new Restriction("external","人工限制",null));
            var blocked=visit(s,t,3000);assertFalse(blocked.allowed());assertEquals(0,blocked.blockedUntil());
            assertFalse(new ApiRiskException(new BehaviorEvent("u","video","v"),blocked).getHeaders().containsKey("Retry-After"));
        }
        p.setMode(RiskProperties.Mode.BLOCK); c.release(USER); c.decline=true;
        try(var s=service(p,new Store(),t,List.of(),c)) {
            visit(s,t,0);visit(s,t,1000);assertTrue(visit(s,t,2000).allowed());
            c.decline=false;c.permanent=true;
            assertNull(visit(s,t,3000).restriction().expiresAt());
        }
    }
    @Test void failedControlRetriesWithoutDoubleCountingAndFailuresAreNotAllows() {
        var p=config();p.setMode(RiskProperties.Mode.BLOCK);p.getBehaviors().get("video").setComponents(List.of("custom"));
        var d=new CountingDetector("custom",true);var t=new Time();var c=new Control(t);c.fail=true;
        try(var s=service(p,new Store(),t,List.of(d),c)) {
            var event=new BehaviorEvent("u","video","v","stable");
            assertThrows(IllegalStateException.class,()->s.checkAndRecord(event));
            c.fail=false;assertFalse(s.checkAndRecord(event).allowed());
            assertEquals(1,d.calls.get());assertEquals(2,c.calls.get());
        }
    }
    @Test void oldAllowAndFullMemoryNeverBypassBusinessRestriction() {
        var p=small();p.getMemory().setMaximumSize(1);var t=new Time();var c=new Control(t);
        try(var s=service(p,new Store(),t,List.of(),c)) {
            var event=new BehaviorEvent("u","video","v","retry");
            assertTrue(s.checkAndRecord(event).allowed());
            c.restrictions.put(USER,new Restriction("one","external",null));
            assertFalse(s.checkAndRecord(event).allowed());
            var other=new Subject("other","video");c.restrictions.put(other,new Restriction("two","external",null));
            assertFalse(s.checkAndRecord(new BehaviorEvent("other","video","v")).allowed());
            assertEquals(Status.CAPACITY,s.checkAndRecord(new BehaviorEvent("third","video","v")).status());
        }
        RiskControl broken=new Control(t) { public Optional<Restriction> findRestriction(Subject s) { throw new IllegalStateException("offline"); } };
        try(var s=service(p,new Store(),t,List.of(),broken)) {
            assertThrows(IllegalStateException.class,()->visit(s,t,0));
        }
    }
    @Test void concurrentSameSubjectDedupAndIsolation() throws Exception {
        var p=config();p.getBehaviors().get("video").setComponents(List.of("counter"));
        var d=new CountingDetector("counter",false);var t=new Time();
        try(var s=service(p,new Store(),t,List.of(d),null);var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks=new ArrayList<Future<?>>();
            for(int i=0;i<1000;i++)tasks.add(pool.submit(()->s.checkAndRecord(new BehaviorEvent("u","video","v"))));
            for(var task:tasks)task.get();
            for(int i=0;i<10;i++)s.checkAndRecord(new BehaviorEvent("u","video","v","stable"));
            assertEquals(1001,d.calls.get());
            s.checkAndRecord(new BehaviorEvent("other","video","v","stable"));assertEquals(1002,d.calls.get());
        }
    }
    @Test void queueOverflowAndRetryNeverPerformStoreWritesOnCaller() throws Exception {
        var p=small();p.getAsync().setBatchSize(2);p.getAsync().setQueueCapacity(2);
        var store=new Store();store.failEvents=100;var t=new Time();
        try(var s=service(p,store,t)) {
            for(int i=0;i<100;i++)visit(s,t,i);
            s.flush().get(5,TimeUnit.SECONDS);assertTrue(s.metrics().droppedEvents()>0);
            assertEquals(100,s.inspect(USER).orElseThrow().state().components().getFirst().statistics().get("normalVideos"));
            store.failEvents=0;t.now.set(10_000);s.flush().get(5,TimeUnit.SECONDS);
            assertFalse(store.events.isEmpty());assertTrue(store.ioThreads.stream().allMatch(n->n.equals("security-risk-store")));
        }
    }
    @Test void startupRecoveryCompletesBeforeChecksAndNoTrafficIsMergedIntoOldSnapshots() throws Exception {
        var p=small();p.getMemory().setRestoreOnStartup(true);var t=new Time();
        var entered=new CountDownLatch(1);var resume=new CountDownLatch(1);
        var store=new Store(){public void restore(long cutoff,int limit,Consumer<Restored> consumer)throws Exception{
            entered.countDown();assertTrue(resume.await(5,TimeUnit.SECONDS));
        }};
        try(var s=new DefaultRiskService(p,store,List.of(),null,t);var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var started=executor.submit(s::start);assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->visit(s,t,0));resume.countDown();started.get();
            assertTrue(visit(s,t,0).allowed());
        } finally {resume.countDown();}
    }
    @Test void transactionMisuseDisabledBehaviorAndExemptionRemainExplicit() {
        var p=small();var t=new Time();
        try(var s=service(p,new Store(),t)) {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try {assertThrows(IllegalStateException.class,()->visit(s,t,0));}
            finally {org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
            s.exempt(USER,Duration.ofSeconds(2));assertEquals(Status.EXEMPT,visit(s,t,1000).status());
            assertTrue(visit(s,t,2000).matches().isEmpty());
            assertThrows(IllegalArgumentException.class,()->s.checkAndRecord(new BehaviorEvent("u","unknown","v")));
        }
        p.getBehaviors().get("video").setEnabled(false);
        try(var s=service(p,new Store(),t)){assertEquals(Status.DISABLED,visit(s,t,0).status());}
    }
    @Test void sharedCustomStateCanDistinguishBusinessResetFromLocalEviction() {
        var p=config();p.getBehaviors().get("video").setComponents(List.of("shared"));
        var evicted=new AtomicInteger();
        var d=new CountingDetector("shared",false){public void evict(Subject subject){evicted.incrementAndGet();}};
        var t=new Time();
        try(var s=service(p,new Store(),t,List.of(d),null)) {
            visit(s,t,0);s.release(USER);assertEquals(1,d.resets.get());
        }
        assertEquals(1,d.resets.get());assertEquals(1,evicted.get());
    }
}
