package com.yulinlin.security.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.risk.RiskModels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Counts completed ten-minute buckets; a high event count cannot replace inactive time. */
public final class ActiveAccessDetector implements PersistedRiskDetector {
    private static final long BUCKET = 600_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RiskProperties properties;
    private final Map<Subject, Tracking> subjects = new ConcurrentHashMap<>();
    public ActiveAccessDetector(RiskProperties properties) { this.properties = properties; }
    @Override public String id() { return "active-access"; }
    @Override public Detection detect(BehaviorEvent event, long now) {
        var cfg = properties.getBehaviors().get(event.behaviorType()).getActiveAccess();
        var s = subjects.computeIfAbsent(event.subject(), key -> new Tracking(now));
        long end = Math.floorDiv(now, BUCKET) * BUCKET, start = end - cfg.getWindow().toMillis();
        s.buckets.headMap(start, false).clear();
        s.buckets.merge(end, 1L, Long::sum);
        var completed = s.buckets.subMap(start, true, end, false);
        long count = completed.values().stream().mapToLong(Long::longValue).sum();
        long total = cfg.getWindow().toMillis() / BUCKET;
        var stats = Map.of("requests", count, "activeBuckets", (long) completed.size(), "totalBuckets", total);
        if (s.observedSince > start)
            return new Detection(id(), DetectionStatus.OBSERVING, "尚未形成完整观察窗口", List.of(), stats);
        boolean matched = completed.size() >= Math.ceil(total * cfg.getActiveRatio()) && count >= cfg.getMinRequests();
        var hits = matched ? List.of(new Match(id(), "continuous", start, end, "持续访问覆盖率及请求数达到阈值", stats)) : List.<Match>of();
        return new Detection(id(), matched ? DetectionStatus.MATCHED : DetectionStatus.NORMAL, null, hits, stats);
    }
    @Override public void reset(Subject subject) { subjects.remove(subject); }
    @Override public String snapshot(Subject subject) {
        var state = subjects.get(subject);
        if (state == null) return null;
        try { return JSON.writeValueAsString(new Saved(state.observedSince, state.buckets)); }
        catch (Exception error) { throw new IllegalStateException("持续访问快照失败", error); }
    }
    @Override public void restore(Subject subject, String payload, long now) {
        try {
            var saved = JSON.readValue(payload, Saved.class);
            var state = new Tracking(saved.observedSince);
            long cutoff = Math.floorDiv(now, BUCKET) * BUCKET
                    - properties.getBehaviors().get(subject.behaviorType()).getActiveAccess().getWindow().toMillis();
            saved.buckets.forEach((at, count) -> { if (at >= cutoff && at <= now) state.buckets.put(at, count); });
            subjects.put(subject, state);
        } catch (Exception error) { throw new IllegalStateException("持续访问快照恢复失败", error); }
    }
    private record Saved(long observedSince, Map<Long, Long> buckets) {}
    private static final class Tracking {
        final long observedSince;
        final NavigableMap<Long, Long> buckets = new TreeMap<>();
        Tracking(long now) { observedSince = now; }
    }
}
