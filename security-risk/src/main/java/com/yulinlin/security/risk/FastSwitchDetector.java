package com.yulinlin.security.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.risk.RiskModels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Distinct-resource counts over two exact rolling time windows. No IO. */
public final class FastSwitchDetector implements PersistedRiskDetector {
    private final RiskProperties properties;
    private final Map<Subject, Tracking> subjects = new ConcurrentHashMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    public FastSwitchDetector(RiskProperties properties) { this.properties = properties; }
    @Override public String id() { return "fast-switch"; }

    @Override public Detection detect(BehaviorEvent event, long now) {
        var cfg = properties.getBehaviors().get(event.behaviorType()).getFastSwitch();
        var state = subjects.computeIfAbsent(event.subject(), key -> new Tracking());
        now = Math.max(now, state.lastAt);
        boolean different = event.resourceId() != null && state.lastResource != null
                && !event.resourceId().equals(state.lastResource);
        long gap = state.lastAt < 0 ? Long.MAX_VALUE : now - state.lastAt;
        int cap = properties.getMemory().getMaximumEventsPerSubject();
        state.rapid.add(now, state.lastAt, event.resourceId(), different && gap < cfg.getRapid().getGap().toMillis(), cfg.getRapid(), cap);
        state.normal.add(now, state.lastAt, event.resourceId(), different && gap <= cfg.getNormal().getGap().toMillis(), cfg.getNormal(), cap);
        state.lastAt = now; state.lastResource = event.resourceId();
        var stats = Map.of("rapidVideos", (long) state.rapid.videos.size(), "rapidFastVideos", state.rapid.fastVideos,
                "normalVideos", (long) state.normal.videos.size(), "normalFastVideos", state.normal.fastVideos);
        if (event.resourceId() == null)
            return new Detection(id(), DetectionStatus.OBSERVING, "缺少 resourceId，当前事件不能判断快切", List.of(), stats);
        var hits = new ArrayList<Match>();
        boolean rapidComplete = now >= state.rapid.incompleteUntil;
        boolean normalComplete = now >= state.normal.incompleteUntil;
        if (rapidComplete && state.rapid.fastVideos >= cfg.getRapid().getMinVideos())
            hits.add(hit("rapid", now, cfg.getRapid(), state.rapid, "极速快切视频数达到阈值"));
        if (normalComplete && state.normal.videos.size() >= cfg.getNormal().getMinVideos()
                && state.normal.fastVideos >= Math.ceil(state.normal.videos.size() * cfg.getNormal().getMinRatio()))
            hits.add(hit("normal", now, cfg.getNormal(), state.normal, "普通快切视频数及占比达到阈值"));
        var status = !hits.isEmpty() ? DetectionStatus.MATCHED
                : !rapidComplete || !normalComplete ? DetectionStatus.CAPACITY : DetectionStatus.NORMAL;
        return new Detection(id(), status, status == DetectionStatus.CAPACITY ? "窗口记录容量不足，统计不完整"
                : null, hits, stats);
    }
    private Match hit(String tier, long now, RiskProperties.SwitchTier config, Window window, String reason) {
        return new Match(id(), tier, now - config.getWindow().toMillis(), now, reason,
                Map.of("videos", (long) window.videos.size(), "fastVideos", window.fastVideos));
    }
    @Override public void reset(Subject subject) { subjects.remove(subject); }
    @Override public String snapshot(Subject subject) {
        var state = subjects.get(subject);
        if (state == null) return null;
        try { return JSON.writeValueAsString(new Saved(state.lastAt, state.lastResource,
                state.rapid.save(), state.normal.save())); }
        catch (Exception error) { throw new IllegalStateException("快切快照失败", error); }
    }
    @Override public void restore(Subject subject, String payload, long now) {
        try {
            var saved = JSON.readValue(payload, Saved.class);
            var cfg = properties.getBehaviors().get(subject.behaviorType()).getFastSwitch();
            var state = new Tracking(); state.lastAt = saved.lastAt; state.lastResource = saved.lastResource;
            state.rapid.load(saved.rapid, now, cfg.getRapid(), properties.getMemory().getMaximumEventsPerSubject());
            state.normal.load(saved.normal, now, cfg.getNormal(), properties.getMemory().getMaximumEventsPerSubject());
            subjects.put(subject, state);
        } catch (Exception error) { throw new IllegalStateException("快切快照恢复失败", error); }
    }
    private record Saved(long lastAt, String lastResource, SavedWindow rapid, SavedWindow normal) {}
    private record SavedWindow(List<SavedVisit> visits, long incompleteUntil) {}
    private record SavedVisit(long at, long previousAt, String resource, boolean fast) {}
    private static final class Tracking {
        long lastAt = -1; String lastResource;
        final Window rapid = new Window(), normal = new Window();
    }
    private static final class Visit {
        final long at, previousAt; final String resource; boolean fast;
        Visit(long at, long previousAt, String resource, boolean fast) {
            this.at = at; this.previousAt = previousAt; this.resource = resource; this.fast = fast;
        }
    }
    private static final class Counts { long total, fast; }
    private static final class Window {
        final Deque<Visit> visits = new ArrayDeque<>(), edges = new ArrayDeque<>();
        final Map<String, Counts> videos = new HashMap<>();
        long fastVideos, incompleteUntil;
        void add(long now, long previousAt, String resource, boolean fast, RiskProperties.SwitchTier config, int cap) {
            long cutoff = now - config.getWindow().toMillis();
            expire(cutoff);
            if (resource == null) return;
            if (visits.size() >= cap) {
                incompleteUntil = Math.addExact(now, config.getWindow().toMillis());
                return;
            }
            append(new Visit(now, previousAt, resource, fast && previousAt >= cutoff));
        }
        void append(Visit visit) {
            visits.addLast(visit);
            Counts count = videos.computeIfAbsent(visit.resource, key -> new Counts()); count.total++;
            if (visit.fast) { if (count.fast++ == 0) fastVideos++; edges.addLast(visit); }
        }
        void expire(long cutoff) {
            // An edge counts only while both endpoints are in the window.
            while (!edges.isEmpty() && edges.getFirst().previousAt < cutoff) {
                Visit visit = edges.removeFirst(); visit.fast = false;
                if (--videos.get(visit.resource).fast == 0) fastVideos--;
            }
            while (!visits.isEmpty() && visits.getFirst().at < cutoff) {
                Visit visit = visits.removeFirst();
                if (--videos.get(visit.resource).total == 0) videos.remove(visit.resource);
            }
        }
        SavedWindow save() {
            return new SavedWindow(visits.stream().map(v -> new SavedVisit(v.at, v.previousAt, v.resource, v.fast)).toList(), incompleteUntil);
        }
        void load(SavedWindow saved, long now, RiskProperties.SwitchTier cfg, int cap) {
            if (saved.visits.size() > cap) throw new IllegalArgumentException("快照超过窗口容量");
            incompleteUntil = saved.incompleteUntil;
            long cutoff = now - cfg.getWindow().toMillis();
            for (SavedVisit v : saved.visits) if (v.at >= cutoff && v.at <= now)
                append(new Visit(v.at, v.previousAt, v.resource, v.fast && v.previousAt >= cutoff));
        }
    }
}
