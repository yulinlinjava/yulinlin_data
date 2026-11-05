package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class RiskServiceTest {
    static final Subject USER = new Subject("u", "video");
    static RiskProperties config() {
        var p = new RiskProperties();
        p.setEnabled(true); p.setMode(RiskProperties.Mode.BLOCK);
        p.getMemory().setRestoreOnStartup(false);
        p.getAsync().setFlushInterval(Duration.ofHours(1));
        p.getAsync().setSnapshotInterval(Duration.ofHours(1));
        p.getAsync().setBatchSize(500);
        p.getAsync().setQueueCapacity(5000);
        var behavior = new RiskProperties.Behavior();
        var rule = new RiskProperties.Rule();
        rule.setId("continuous"); rule.setType("ACTIVE_MINUTES");
        rule.setWindow(Duration.ofMinutes(2)); rule.setMinActiveMinutes(2);
        behavior.getRules().add(rule); p.getBehaviors().put("video", behavior);
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
        volatile int failEvents;
        final List<String> ioThreads = new CopyOnWriteArrayList<>();
        public void initialize() {}
        public void writeEvents(List<Event> rows) {
            ioThreads.add(Thread.currentThread().getName());
            if (failEvents-- > 0) throw new IllegalStateException("simulated outage");
            rows.forEach(e -> events.putIfAbsent(e.id(), e));
        }
        public void writeSnapshot(Snapshot snapshot) {
            ioThreads.add(Thread.currentThread().getName());
            snapshots.put(snapshot.state().streamId(), snapshot);
        }
        public void restore(long cutoff, int limit, Consumer<Restored> consumer) throws Exception {}
        public List<Event> history(Subject s, long from, long to, int offset, int limit) { return List.copyOf(events.values()); }
        public void cleanup(long cutoff, long now) {}
    }
    static DefaultRiskService service(RiskProperties p, Store store, Time time) {
        var service = new DefaultRiskService(p, store, List.of(), List.of(), time); service.start(); return service;
    }
    static Decision visit(DefaultRiskService service, Time time, long at) {
        time.now.set(at);
        return service.checkAndRecord(new BehaviorEvent("u", "video", "v"));
    }
    @Test void completedMinutesTriggerBeforeBusinessAndReleaseStartsNewObservation() throws Exception {
        var time = new Time(); var store = new Store();
        try (var service = service(config(), store, time)) {
            assertEquals(Status.OBSERVING, visit(service, time, 0).status());
            assertTrue(visit(service, time, 60_000).allowed());
            assertTrue(visit(service, time, 119_999).allowed());
            Decision result = visit(service, time, 120_000);
            assertFalse(result.allowed()); assertEquals(2, result.matches().getFirst().activeMinutes());
            assertFalse(visit(service, time, 121_000).allowed());
            service.release(USER);
            assertEquals(Status.OBSERVING, visit(service, time, 122_000).status());
            service.flush().get(5, TimeUnit.SECONDS);
            assertEquals(6, store.events.size());
            assertTrue(store.ioThreads.stream().allMatch(n -> n.equals("security-risk-store")));
        }
    }
    @Test void denseRuleAllowsPausesAndRulesAreOr() {
        var p = config(); var time = new Time();
        var continuous = p.getBehaviors().get("video").getRules().getFirst();
        continuous.setWindow(Duration.ofMinutes(6)); continuous.setMinActiveMinutes(6);
        var dense = new RiskProperties.Rule();
        dense.setId("dense"); dense.setType("DENSE_ACCESS"); dense.setWindow(Duration.ofMinutes(6));
        dense.setMinActiveMinutes(4); dense.setMinRequests(8); dense.setShortGap(Duration.ofSeconds(10));
        dense.setMinShortGapRatio(0.5); p.getBehaviors().get("video").getRules().add(dense);
        try (var service = service(p, new Store(), time)) {
            for (long at : new long[]{0,5000,60_000,65_000,180_000,185_000,240_000,245_000}) visit(service,time,at);
            var result = visit(service,time,360_000);
            assertFalse(result.allowed());
            assertEquals(List.of("dense"), result.matches().stream().map(Match::ruleId).toList());
            assertEquals(8, result.matches().getFirst().requests());
            assertEquals(7, result.matches().getFirst().intervals());
            assertEquals(4, result.matches().getFirst().shortIntervals());
        }
    }
    @Test void windowDropsOutsideEdgeAndCountsCrossMinuteGaps() {
        var rule = new RiskProperties.Rule();
        rule.setId("d"); rule.setType("DENSE_ACCESS"); rule.setWindow(Duration.ofMinutes(2));
        rule.setMinRequests(2); rule.setMinActiveMinutes(1); rule.setMinShortGapRatio(1);
        var minute = new Minute(60_000, 2, 2, 0, Map.of("d",1L), 1);
        var hit = RiskRule.denseAccess().evaluate(rule,180_000,List.of(minute)).orElseThrow();
        assertEquals(1,hit.intervals()); assertEquals(1,hit.shortIntervals());
        var time = new Time(); var p = config();
        try(var service = service(p,new Store(),time)) {
            visit(service,time,59_000); visit(service,time,61_000);
            var bucket = service.inspect(USER).orElseThrow().minutes().getLast();
            assertEquals(1,bucket.intervals());
            assertEquals(1,bucket.shortIntervals().get("continuous"));
        }
    }
    @Test void concurrentSameUserDoesNotLoseCountsAndEventIdIsLocallyIdempotent() throws Exception {
        var time = new Time();
        try (var service = service(config(), new Store(), time);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int i=0;i<1000;i++) tasks.add(executor.submit(() -> service.checkAndRecord(new BehaviorEvent("u","video","same-video"))));
            for(var task:tasks) task.get();
            assertEquals(1000,service.inspect(USER).orElseThrow().minutes().getFirst().requests());
            for (int i=0;i<10;i++) service.checkAndRecord(new BehaviorEvent("u","video","same-video","stable"));
            assertEquals(1001,service.inspect(USER).orElseThrow().minutes().getFirst().requests());
        }
    }
    @Test void overflowDoesNotBlockAndCountsRemainWhileFailedPersistenceRetries() throws Exception {
        var p=config(); p.getAsync().setBatchSize(2); p.getAsync().setQueueCapacity(2);
        var store=new Store(); store.failEvents=100;
        var time=new Time();
        try(var service=service(p,store,time)) {
            for(int i=0;i<100;i++) service.checkAndRecord(new BehaviorEvent("u","video","v"));
            service.flush().get(5,TimeUnit.SECONDS);
            assertTrue(service.metrics().droppedEvents()>0);
            assertEquals(100,service.inspect(USER).orElseThrow().minutes().getFirst().requests());
            store.failEvents=0;
            time.now.set(10_000);
            service.flush().get(5,TimeUnit.SECONDS);
            assertFalse(store.events.isEmpty());
        }
    }
    @Test void observationCapacityExemptionClockRollbackAndUnknownBehaviorAreExplicit() {
        var p=config(); p.setMode(RiskProperties.Mode.OBSERVE); p.getMemory().setMaximumSize(1);
        var time=new Time();
        try(var service=service(p,new Store(),time)) {
            visit(service,time,0); visit(service,time,60_000);
            var result=visit(service,time,120_000);
            assertTrue(result.allowed()); assertEquals(Status.SUSPECTED,result.status());
            assertEquals(Status.CAPACITY,service.checkAndRecord(new BehaviorEvent("other","video","v")).status());
            service.exempt(USER,Duration.ofMinutes(1));
            assertEquals(Status.EXEMPT,visit(service,time,121_000).status());
            assertEquals(Status.OBSERVING,visit(service,time,181_000).status());
            visit(service,time,180_000);
            assertEquals(181_000,service.inspect(USER).orElseThrow().state().lastAt());
            assertThrows(IllegalArgumentException.class,()->service.checkAndRecord(new BehaviorEvent("u","typo","v")));
        }
    }
    @Test void expiredBlockDoesNotImmediatelyReblockFromOldEvidence() {
        var p=config(); p.setBlockDuration(Duration.ofSeconds(5)); var time=new Time();
        try(var service=service(p,new Store(),time)) {
            visit(service,time,0); visit(service,time,60_000);
            assertFalse(visit(service,time,120_000).allowed());
            assertEquals(Status.OBSERVING,visit(service,time,125_000).status());
        }
    }
    @Test void databaseOutageNeverRunsIoOnTheRequestThread() throws Exception {
        var store=new Store(); store.failEvents=1; var time=new Time();
        try(var service=service(config(),store,time)) {
            visit(service,time,0);
            assertTrue(store.ioThreads.isEmpty());
            service.flush().get(5,TimeUnit.SECONDS);
            assertEquals(1,service.metrics().storeFailures());
            time.now.set(5000); service.flush().get(5,TimeUnit.SECONDS);
            assertEquals(1,store.events.size());
        }
    }

    @Test void retryAfterBlockExpiryAndBusinessTransactionMisuse() {
        var p=config();p.setBlockDuration(Duration.ofSeconds(5));var time=new Time();
        try(var service=service(p,new Store(),time)) {
            visit(service,time,0);visit(service,time,60_000);time.now.set(120_000);
            var request=new BehaviorEvent("u","video","v","retry");
            assertFalse(service.checkAndRecord(request).allowed());
            time.now.set(125_000);
            assertEquals(Status.OBSERVING,service.checkAndRecord(request).status());
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class,()->service.checkAndRecord(request)); }
            finally { org.springframework.transaction.support.TransactionSynchronizationManager.clear(); }
        }
    }

    @Test void recoveryPreservesLiveRequestsAndLocalReleaseWins() throws Exception {
        var p=config();p.getMemory().setRestoreOnStartup(true);var time=new Time();time.now.set(120_000);
        var enter=new CountDownLatch(1);var resume=new CountDownLatch(1);
        var store=new Store() {
            @Override public void restore(long cutoff,int limit,Consumer<Restored> consumer) throws Exception {
                enter.countDown();assertTrue(resume.await(5,TimeUnit.SECONDS));
                for(var snapshot:snapshots.values()) consumer.accept(new Restored(snapshot.state(),snapshot.minutes()));
            }
        };
        // Obtain the production signature via the public view, without depending on its hash encoding.
        String signature;
        try(var original=service(config(),new Store(),new Time())) {
            original.checkAndRecord(new BehaviorEvent("u","video","v"));
            signature=original.inspect(USER).orElseThrow().state().signature();
        }
        var saved=new State(USER,"old",signature,1,0,60_000,0,0,0,List.of(),60_000);
        store.snapshots.put("old",new Snapshot(saved,List.of(new Minute(0,1,0,-1,Map.of(),1),new Minute(60_000,1,1,0,Map.of(),1))));
        var released=new Subject("released","video");
        store.snapshots.put("restricted",new Snapshot(new State(released,"restricted",signature,1,0,60_000,300_000,0,0,List.of(),60_000),List.of()));
        try(var service=service(p,store,time)) {
            assertTrue(enter.await(5,TimeUnit.SECONDS));
            visit(service,time,120_000);service.release(released);
            resume.countDown();service.flush().get(5,TimeUnit.SECONDS);
            assertEquals(3,service.inspect(USER).orElseThrow().minutes().stream().mapToLong(Minute::requests).sum());
            assertEquals(0,service.inspect(released).orElseThrow().state().blockedUntil());
            assertFalse(visit(service,time,120_000).allowed());
            service.release(USER);assertTrue(service.inspect(USER).orElseThrow().minutes().isEmpty());
        } finally { resume.countDown(); }
    }
}

