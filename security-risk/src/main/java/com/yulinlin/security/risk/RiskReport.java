package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.Detection;
import com.yulinlin.security.risk.RiskModels.Subject;
import java.util.List;
import java.util.Map;

/** JSON-ready, single-subject history report. Epoch milliseconds; ratios are 0..1, null when undefined. */
public record RiskReport(Subject subject, long from, long to, String zoneId, Status status,
                         boolean suspected, boolean incomplete, Summary summary, Rules rules,
                         Switches switches, Activity activity, ActiveWindow bestActiveWindow,
                         List<RuleHit> hits, List<Detection> latestComponents,
                         List<Bucket> minutes, List<Bucket> hours) {
    public RiskReport {
        hits = List.copyOf(hits); latestComponents = List.copyOf(latestComponents);
        minutes = List.copyOf(minutes); hours = List.copyOf(hours);
    }
    public enum Status { NO_DATA, DISABLED, OBSERVING, NOT_MATCHED, SUSPECTED, INCOMPLETE }
    public record Summary(int inputCount, int requestCount, int duplicateCount, int conflictingDuplicateCount,
                          long distinctResources, long missingResourceCount, Long firstEventAt,
                          Long lastEventAt, long spanMillis) {}
    public record SwitchRule(long windowMillis, long gapMillis, boolean gapInclusive, int minVideos, Double minRatio) {}
    public record ActiveRule(long windowMillis, long bucketMillis, long minRequests, double minActiveRatio) {}
    public record Rules(SwitchRule rapid, SwitchRule normal, ActiveRule activeAccess, List<String> customComponents) {
        public Rules { customComponents = List.copyOf(customComponents); }
    }
    /** These are raw adjacent DIFFERENT-resource transitions, not distinct target-video counts. */
    public record Switches(long differentResourceSwitches, long sameResourceTransitions, long sameTimestampSwitches,
                           SwitchMetric rapid, SwitchMetric normal) {}
    public record SwitchMetric(long gapMillis, boolean gapInclusive, long count, Double ratio, long longestRun) {}
    /** Minutes/hours use calendar buckets. Five-minute peak uses five consecutive minute buckets. */
    public record Activity(int activeMinutes, int longestConsecutiveMinutes, long peakMinuteRequests,
                           long peakFiveMinuteRequests, long longestGapMillis, long sessionBreakGapMillis,
                           Segment longestVisit) {}
    /** First/last request span inside a segment; a gap strictly above sessionBreakGapMillis starts a new one. */
    public record Segment(Long start, Long end, long durationMillis, long requestCount) {}
    /** Nonempty minutes/hours are sparse; active-window ten-minute buckets include zero-count buckets. */
    public record Bucket(long start, long end, long count) {}
    public record FastWindow(long requestCount, long distinctVideos, long differentResourceSwitches,
                             long fastSwitchCount, Double fastSwitchRatio, long distinctFastVideos,
                             Double fastVideoRatio, long longestFastSwitchRun, long sameTimestampSwitches) {}
    /** [start,end); complete is observation coverage, not proof of the source database's completeness. */
    public record ActiveWindow(long start, long end, boolean complete, long requestCount, int activeBuckets,
                               int totalBuckets, double activeRatio, int activeMinutes, long longestGapMillis,
                               List<Bucket> buckets) {
        public ActiveWindow { buckets = List.copyOf(buckets); }
    }
    /** The first matching window is preserved. matchedRequestCount counts matching requests, not separate attacks. */
    public record RuleHit(String componentId, String ruleId, long firstMatchedAt, long lastMatchedAt,
                          long matchedRequestCount, long windowStart, long windowEnd, Boolean endInclusive,
                          String reason, Map<String, Long> statistics, FastWindow fastSwitch, ActiveWindow activeAccess) {
        public RuleHit { statistics = Map.copyOf(statistics); }
    }
}
