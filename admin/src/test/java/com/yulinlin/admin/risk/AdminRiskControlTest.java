package com.yulinlin.admin.risk;

import com.yulinlin.security.risk.*;
import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** A runnable business-control example, intentionally not a production Bean or shared database. */
class AdminRiskControlTest {
    @Test @DisplayName("业务自己限制十分钟：命中、阻止重试、到期重新观察、人工解除")
    void businessOwnsRestrictionDuration() throws Exception {
        var time = new TestClock();
        var control = new BusinessControl(time);
        var store = new TestStore();
        var p = AdminRiskReplayTest.config();
        p.setMode(RiskProperties.Mode.BLOCK);
        p.getMemory().setRestoreOnStartup(false);
        var subject = new Subject("demo-user", "video-play-url");
        try (var service = new DefaultRiskService(p, store, List.of(), control, time)) {
            service.start();
            Decision decision = null;
            for (int i = 0; i <= 50; i++) {
                time.now.set(i * 1000L);
                decision = service.checkAndRecord(new BehaviorEvent(subject.userId(), subject.behaviorType(), "video-" + i));
                if (i < 50) assertTrue(decision.allowed());
            }
            assertFalse(decision.allowed());
            assertEquals(650_000, decision.blockedUntil()); // Business selected 10m, not a framework duration.
            System.out.printf("业务限制已生效：%s，到期毫秒=%d%n", decision.restriction().reason(), decision.blockedUntil());
            assertFalse(service.checkAndRecord(new BehaviorEvent(subject.userId(), subject.behaviorType(), "retry")).allowed());
            assertEquals(1, control.actions);
            time.now.set(650_000);
            assertTrue(service.checkAndRecord(new BehaviorEvent(subject.userId(), subject.behaviorType(), "after-expiry")).allowed());
            control.restrictions.put(subject, new Restriction("manual", "人工永久限制", null));
            var permanent = service.checkAndRecord(new BehaviorEvent(subject.userId(), subject.behaviorType(), "blocked"));
            assertFalse(permanent.allowed());
            assertFalse(new ApiRiskException(new BehaviorEvent(subject.userId(), subject.behaviorType(), "blocked"), permanent)
                    .getHeaders().containsKey("Retry-After"));
            service.release(subject);
            assertTrue(service.checkAndRecord(new BehaviorEvent(subject.userId(), subject.behaviorType(), "released")).allowed());
            service.flush().get(5, TimeUnit.SECONDS);
            assertFalse(store.events.isEmpty());
            System.out.printf("到期和人工解除均重新观察；审计事件=%d 条%n", store.events.size());
        }
    }

    // Replace this map with your own cache/DAO and implement cross-instance idempotency in production.
    static final class BusinessControl implements RiskControl {
        final Clock clock;
        final Map<Subject, Restriction> restrictions = new ConcurrentHashMap<>();
        int actions;
        BusinessControl(Clock clock) { this.clock = clock; }
        public Optional<Restriction> findRestriction(Subject subject) {
            var value = restrictions.get(subject);
            if (value != null && value.expiresAt() != null && value.expiresAt() <= clock.millis()) {
                restrictions.remove(subject, value); value = null;
            }
            return Optional.ofNullable(value);
        }
        public Optional<Restriction> onRisk(Subject subject, List<Match> hits) {
            actions++;
            var restriction = restrictions.computeIfAbsent(subject, key -> new Restriction(UUID.randomUUID().toString(),
                    "播放链接访问异常", clock.millis() + Duration.ofMinutes(10).toMillis()));
            return Optional.of(restriction);
        }
        public void release(Subject subject) { restrictions.remove(subject); }
    }
    static final class TestClock extends Clock {
        final AtomicLong now = new AtomicLong();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        public long millis() { return now.get(); }
    }
    static final class TestStore implements BehaviorStore {
        final List<Event> events = new CopyOnWriteArrayList<>();
        public void initialize() {}
        public void writeEvents(List<Event> values) { events.addAll(values); }
        public void writeSnapshot(Snapshot snapshot) {}
        public void restore(long cutoff, int limit, Consumer<Restored> consumer) {}
        public List<Event> history(Subject subject, long from, long to, int offset, int limit) { return List.copyOf(events); }
        public void cleanup(long cutoff, long now) {}
    }
}
