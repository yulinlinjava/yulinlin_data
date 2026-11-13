package com.yulinlin.security.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import com.yulinlin.security.risk.RiskModels.ReplayEvent;
import com.yulinlin.security.risk.RiskModels.ReplayResult;
import com.yulinlin.security.risk.RiskModels.Subject;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Replays trusted historical records through the default risk engine, without Spring or a database.
 * Each call starts with empty state and returns decisions in chronological order.
 * Custom detector factories must create isolated instances. Business RiskControl is never invoked.
 */
public final class RiskReplay {
    private final RiskProperties properties;
    private final Supplier<List<RiskDetector>> detectors;

    public RiskReplay(RiskProperties properties) { this(properties, List::of); }

    public RiskReplay(RiskProperties properties, Supplier<List<RiskDetector>> customDetectors) {
        this.properties = new ObjectMapper().findAndRegisterModules()
                .convertValue(Objects.requireNonNull(properties, "properties"), RiskProperties.class);
        this.properties.validate();
        this.detectors = Objects.requireNonNull(customDetectors, "customDetectors");
    }

    /** Builds a JSON-ready report for one subject over [from,to), using UTC display buckets. */
    public RiskReport report(Subject subject, long from, long to, List<ReplayEvent> events) {
        return report(subject, from, to, ZoneOffset.UTC, events);
    }

    /**
     * Uses the same detectors as replay; never invokes business RiskControl or a database.
     * The caller supplies the complete requested history, not one page. No final event is invented.
     */
    public RiskReport report(Subject subject, long from, long to, ZoneId zone, List<ReplayEvent> events) {
        return RiskReportBuilder.build(this, properties, subject, from, to, zone, events);
    }

    /**
     * Stable time ordering preserves the input order for equal timestamps. The input is not modified.
     * No synthetic final request is added: an unfinished observation window remains OBSERVING.
     * The enabled property controls online auto-configuration; explicit replay always runs.
     */
    public List<ReplayResult> replay(List<ReplayEvent> events) {
        Objects.requireNonNull(events, "events");
        var ordered = new ArrayList<IndexedEvent>(events.size());
        int i = 0;
        for (var item : events) {
            var event = Objects.requireNonNull(item, "events[" + i + "]");
            if (!properties.getBehaviors().containsKey(event.behaviorType()))
                throw new IllegalArgumentException("未配置风控行为：" + event.behaviorType());
            Math.addExact(event.occurredAt(), properties.getMemory().getRetention().toMillis());
            ordered.add(new IndexedEvent(i++, event));
        }
        ordered.sort(Comparator.comparingLong(row -> row.event().occurredAt()));
        var clock = new ReplayClock(properties.getMemory().getRetention().toMillis());
        var results = new ArrayList<ReplayResult>(ordered.size());
        try (var engine = DefaultRiskService.forReplay(properties, List.copyOf(detectors.get()), clock, clock)) {
            for (var row : ordered) {
                clock.advance(row.event().occurredAt());
                var decision = engine.checkAndRecord(row.event().event());
                results.add(new ReplayResult(row.index(), row.event(), decision));
            }
        }
        return List.copyOf(results);
    }

    private record IndexedEvent(int index, ReplayEvent event) {}

    private static final class ReplayClock extends Clock implements Ticker {
        private final long retentionMillis;
        private long now, nanos;
        ReplayClock(long retentionMillis) { this.retentionMillis = retentionMillis; }
        void advance(long next) {
            // A gap longer than retention expires every cached result. Cap it to avoid nanos overflow
            // from converting a very long historical gap; the engine still receives the actual time.
            nanos += Math.min(next - now, retentionMillis) * 1_000_000;
            now = next;
        }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
        @Override public long read() { return nanos; }
    }
}
