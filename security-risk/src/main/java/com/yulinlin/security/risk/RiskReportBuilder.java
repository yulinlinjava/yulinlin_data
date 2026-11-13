package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import com.yulinlin.security.risk.RiskReport.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure report aggregation; detector decisions remain the only source of rule hits. */
final class RiskReportBuilder {
    private static final long MINUTE = 60_000, TEN_MINUTES = 600_000, VISIT_GAP = 300_000;

    static RiskReport build(RiskReplay replay, RiskProperties properties, Subject subject, long from, long to,
                            ZoneId zone, List<ReplayEvent> input) {
        Objects.requireNonNull(subject, "subject"); Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(input, "events");
        if (from < 0 || to <= from) throw new IllegalArgumentException("报告范围必须满足 0 <= from < to");
        var config = properties.getBehaviors().get(subject.behaviorType());
        if (config == null) throw new IllegalArgumentException("未配置风控行为：" + subject.behaviorType());
        var ordered = new ArrayList<>(input);
        for (var event : ordered) {
            Objects.requireNonNull(event, "event");
            if (!subject.equals(event.event().subject())) throw new IllegalArgumentException("报告只能包含指定用户和行为");
            if (event.occurredAt() < from || event.occurredAt() >= to)
                throw new IllegalArgumentException("报告事件必须位于 [from,to) 内");
        }
        ordered.sort(Comparator.comparingLong(ReplayEvent::occurredAt));
        var seen = new HashMap<String, ReplayEvent>();
        var events = new ArrayList<ReplayEvent>();
        int duplicates = 0, conflicts = 0;
        for (var event : ordered) {
            var previous = event.eventId() == null ? null : seen.putIfAbsent(event.eventId(), event);
            if (previous == null) events.add(event);
            else { duplicates++; if (!previous.equals(event)) conflicts++; }
        }
        var results = replay.replay(events);
        var counts = new TreeMap<Long, Long>();
        var hours = new TreeMap<Long, Long>();
        for (var event : events) {
            counts.merge(Math.floorDiv(event.occurredAt(), MINUTE) * MINUTE, 1L, Long::sum);
            long hour = Instant.ofEpochMilli(event.occurredAt()).atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli();
            hours.merge(hour, 1L, Long::sum);
        }
        var hitGroups = new LinkedHashMap<String, HitGroup>();
        boolean incomplete = false;
        ReplayResult bestActive = null;
        Detection bestDetection = null;
        for (var result : results) {
            incomplete |= result.decision().status() == RiskModels.Status.CAPACITY;
            for (var detection : result.decision().components()) {
                incomplete |= detection.status() == DetectionStatus.CAPACITY;
                if (detection.componentId().equals("active-access")
                        && (detection.status() == DetectionStatus.NORMAL || detection.status() == DetectionStatus.MATCHED)
                        && (bestDetection == null || betterActive(detection, bestDetection))) {
                    bestActive = result; bestDetection = detection;
                }
            }
            for (var match : result.decision().matches()) {
                String key = match.componentId() + "/" + match.ruleId();
                var group = hitGroups.computeIfAbsent(key, ignored -> new HitGroup(result.event().occurredAt(), result.inputIndex() + 1, match));
                group.count++; group.lastAt = result.event().occurredAt();
            }
        }
        var hits = hitGroups.values().stream().map(group -> hit(group, events, config)).toList();
        var latest = results.isEmpty() ? List.<Detection>of() : results.getLast().decision().components();
        RiskReport.Status status = events.isEmpty() ? RiskReport.Status.NO_DATA : !hits.isEmpty() ? RiskReport.Status.SUSPECTED
                : incomplete ? RiskReport.Status.INCOMPLETE : !config.isEnabled() || config.getComponents().isEmpty() ? RiskReport.Status.DISABLED
                : results.getLast().decision().status() == RiskModels.Status.OBSERVING ? RiskReport.Status.OBSERVING : RiskReport.Status.NOT_MATCHED;
        Long first = events.isEmpty() ? null : events.getFirst().occurredAt();
        Long last = events.isEmpty() ? null : events.getLast().occurredAt();
        var summary = new Summary(input.size(), events.size(), duplicates, conflicts,
                events.stream().map(ReplayEvent::resourceId).filter(Objects::nonNull).distinct().count(),
                events.stream().filter(e -> e.resourceId() == null).count(), first, last, first == null ? 0 : last - first);
        ActiveWindow active = null;
        if (bestActive != null) {
            long end = Math.floorDiv(bestActive.event().occurredAt(), TEN_MINUTES) * TEN_MINUTES;
            active = activeWindow(events, end - config.getActiveAccess().getWindow().toMillis(), end);
        }
        return new RiskReport(subject, from, to, zone.getId(), status, !hits.isEmpty(), incomplete, summary, rules(config),
                switches(events, config), activity(events, counts), active, hits, latest,
                buckets(counts, MINUTE), hourBuckets(hours, zone));
    }

    private static boolean betterActive(Detection next, Detection previous) {
        long difference = next.statistics().getOrDefault("activeBuckets", 0L) - previous.statistics().getOrDefault("activeBuckets", 0L);
        return difference > 0 || difference == 0 && next.statistics().getOrDefault("requests", 0L) > previous.statistics().getOrDefault("requests", 0L);
    }
    private static Rules rules(RiskProperties.Behavior config) {
        boolean enabled = config.isEnabled();
        boolean fast = enabled && config.getComponents().contains("fast-switch");
        boolean active = enabled && config.getComponents().contains("active-access");
        var rapid = config.getFastSwitch().getRapid(); var normal = config.getFastSwitch().getNormal(); var access = config.getActiveAccess();
        return new Rules(fast ? new SwitchRule(rapid.getWindow().toMillis(), rapid.getGap().toMillis(), false, rapid.getMinVideos(), null) : null,
                fast ? new SwitchRule(normal.getWindow().toMillis(), normal.getGap().toMillis(), true, normal.getMinVideos(), normal.getMinRatio()) : null,
                active ? new ActiveRule(access.getWindow().toMillis(), TEN_MINUTES, access.getMinRequests(), access.getActiveRatio()) : null,
                config.getComponents().stream().filter(id -> !id.equals("fast-switch") && !id.equals("active-access")).toList());
    }
    private static RuleHit hit(HitGroup group, List<ReplayEvent> events, RiskProperties.Behavior config) {
        // Later requests may share the first matching timestamp; they were not part of that decision.
        events = events.subList(0, group.eventCount);
        var match = group.first;
        boolean fast = match.componentId().equals("fast-switch");
        boolean active = match.componentId().equals("active-access");
        FastWindow fastWindow = null;
        if (fast) {
            boolean rapid = match.ruleId().equals("rapid");
            var tier = rapid ? config.getFastSwitch().getRapid() : config.getFastSwitch().getNormal();
            var window = window(events, match.windowStart(), match.windowEnd(), true);
            var data = new Transitions(window, tier.getGap().toMillis(), !rapid);
            long videos = window.stream().map(ReplayEvent::resourceId).filter(Objects::nonNull).distinct().count();
            fastWindow = new FastWindow(window.size(), videos, data.different, data.fast, ratio(data.fast, data.different),
                    data.targets.size(), ratio(data.targets.size(), videos), data.longest, data.simultaneous);
        }
        return new RuleHit(match.componentId(), match.ruleId(), group.firstAt, group.lastAt, group.count,
                match.windowStart(), match.windowEnd(), fast ? Boolean.TRUE : active ? Boolean.FALSE : null, match.reason(), match.statistics(), fastWindow,
                active ? activeWindow(events, match.windowStart(), match.windowEnd()) : null);
    }
    private static Switches switches(List<ReplayEvent> events, RiskProperties.Behavior config) {
        long rapidGap = config.getFastSwitch().getRapid().getGap().toMillis();
        long normalGap = config.getFastSwitch().getNormal().getGap().toMillis();
        var rapid = new Transitions(events, rapidGap, false);
        var normal = new Transitions(events, normalGap, true);
        return new Switches(rapid.different, rapid.same, rapid.simultaneous,
                new SwitchMetric(rapidGap, false, rapid.fast, ratio(rapid.fast, rapid.different), rapid.longest),
                new SwitchMetric(normalGap, true, normal.fast, ratio(normal.fast, normal.different), normal.longest));
    }
    private static Activity activity(List<ReplayEvent> events, NavigableMap<Long, Long> minutes) {
        int longestMinutes = 0, run = 0; long previousMinute = Long.MIN_VALUE, peak = 0, peakFive = 0, sum = 0;
        var recent = new ArrayDeque<Map.Entry<Long, Long>>();
        for (var entry : minutes.entrySet()) {
            run = previousMinute != Long.MIN_VALUE && entry.getKey() - previousMinute == MINUTE ? run + 1 : 1;
            previousMinute = entry.getKey(); longestMinutes = Math.max(longestMinutes, run);
            peak = Math.max(peak, entry.getValue());
            while (!recent.isEmpty() && entry.getKey() - recent.getFirst().getKey() >= 5 * MINUTE) sum -= recent.removeFirst().getValue();
            recent.addLast(entry); sum += entry.getValue(); peakFive = Math.max(peakFive, sum);
        }
        Segment best = new Segment(null, null, 0, 0);
        long longestGap = 0; int start = 0;
        for (int i = 0; i < events.size(); i++) {
            long at = events.get(i).occurredAt();
            if (i > 0) {
                long gap = at - events.get(i - 1).occurredAt(); longestGap = Math.max(longestGap, gap);
                if (gap > VISIT_GAP) start = i;
            }
            var segment = new Segment(events.get(start).occurredAt(), at, at - events.get(start).occurredAt(), i - start + 1L);
            if (segment.durationMillis() > best.durationMillis()
                    || segment.durationMillis() == best.durationMillis() && segment.requestCount() > best.requestCount()) best = segment;
        }
        return new Activity(minutes.size(), longestMinutes, peak, peakFive, longestGap, VISIT_GAP, best);
    }
    private static ActiveWindow activeWindow(List<ReplayEvent> events, long start, long end) {
        var selected = window(events, start, end, false);
        var counts = new TreeMap<Long, Long>(); var minutes = new HashSet<Long>();
        long previous = start, longestGap = 0;
        for (var event : selected) {
            long at = event.occurredAt();
            counts.merge(Math.floorDiv(at, TEN_MINUTES) * TEN_MINUTES, 1L, Long::sum);
            minutes.add(Math.floorDiv(at, MINUTE)); longestGap = Math.max(longestGap, at - previous); previous = at;
        }
        longestGap = Math.max(longestGap, end - previous);
        var buckets = new ArrayList<Bucket>();
        for (long at = start; at < end; at += TEN_MINUTES) buckets.add(new Bucket(at, at + TEN_MINUTES, counts.getOrDefault(at, 0L)));
        int total = buckets.size();
        return new ActiveWindow(start, end, !events.isEmpty() && events.getFirst().occurredAt() <= start,
                selected.size(), counts.size(), total, total == 0 ? 0 : (double) counts.size() / total, minutes.size(), longestGap, buckets);
    }
    private static List<ReplayEvent> window(List<ReplayEvent> events, long start, long end, boolean inclusiveEnd) {
        return events.stream().filter(e -> e.occurredAt() >= start && (inclusiveEnd ? e.occurredAt() <= end : e.occurredAt() < end)).toList();
    }
    private static List<Bucket> buckets(NavigableMap<Long, Long> counts, long width) {
        return counts.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getKey() + width, e.getValue())).toList();
    }
    private static List<Bucket> hourBuckets(NavigableMap<Long, Long> counts, ZoneId zone) {
        return counts.entrySet().stream().map(entry -> {
            long end = entry.getKey();
            var next = Instant.ofEpochMilli(end);
            while (end <= entry.getKey()) {
                next = next.plus(1, ChronoUnit.HOURS);
                end = next.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli();
            }
            return new Bucket(entry.getKey(), end, entry.getValue());
        }).toList();
    }
    private static Double ratio(long numerator, long denominator) { return denominator == 0 ? null : (double) numerator / denominator; }
    private static final class HitGroup {
        final long firstAt; final int eventCount; final Match first; long lastAt, count;
        HitGroup(long firstAt, int eventCount, Match first) { this.firstAt = firstAt; this.eventCount = eventCount; this.first = first; }
    }
    private static final class Transitions {
        long different, same, simultaneous, fast, longest;
        final Set<String> targets = new HashSet<>();
        Transitions(List<ReplayEvent> events, long gapLimit, boolean inclusive) {
            long run = 0;
            for (int i = 1; i < events.size(); i++) {
                var previous = events.get(i - 1); var current = events.get(i);
                if (previous.resourceId() == null || current.resourceId() == null) { run = 0; continue; }
                if (previous.resourceId().equals(current.resourceId())) { same++; run = 0; continue; }
                long gap = current.occurredAt() - previous.occurredAt(); different++;
                if (gap == 0) simultaneous++;
                if (inclusive ? gap <= gapLimit : gap < gapLimit) {
                    fast++; targets.add(current.resourceId()); longest = Math.max(longest, ++run);
                } else run = 0;
            }
        }
    }
}
