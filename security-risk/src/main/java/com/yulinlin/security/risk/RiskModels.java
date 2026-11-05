package com.yulinlin.security.risk;

import java.util.List;
import java.util.Map;

/** Immutable public values. Times are server epoch milliseconds, independent of JDBC/JVM time zones. */
public final class RiskModels {
    private RiskModels() {}
    public record Subject(String userId, String behaviorType) {
        public Subject {
            bounded(userId, 128, "userId");
            bounded(behaviorType, 64, "behaviorType");
        }
    }
    public record BehaviorEvent(String userId, String behaviorType, String resourceId, String eventId) {
        public BehaviorEvent {
            new Subject(userId, behaviorType);
            if (resourceId != null) bounded(resourceId, 256, "resourceId");
            if (eventId != null) bounded(eventId, 128, "eventId");
        }
        public BehaviorEvent(String userId, String behaviorType, String resourceId) {
            this(userId, behaviorType, resourceId, null);
        }
        public Subject subject() { return new Subject(userId, behaviorType); }
    }
    public enum Status { OBSERVING, ALLOWED, SUSPECTED, BLOCKED, EXEMPT, DISABLED, CAPACITY }
    public record Match(String ruleId, long windowStart, long windowEnd, long requests,
                        int activeMinutes, long intervals, long shortIntervals) {}
    public record Decision(boolean allowed, Status status, long blockedUntil, List<Match> matches) {
        public Decision { matches = List.copyOf(matches); }
    }
    public record Event(String id, Subject subject, String resourceId, long occurredAt, Decision decision) {}
    public record Minute(long start, long requests, long intervals, long firstPreviousAt,
                         Map<String, Long> shortIntervals, long version) {
        public Minute { shortIntervals = Map.copyOf(shortIntervals); }
    }
    public record State(Subject subject, String streamId, String signature, long version, long observedSince,
                        long lastAt, long blockedUntil, long exemptUntil, long resetAt,
                        List<Match> matches, long updatedAt) {
        public State { matches = List.copyOf(matches); }
    }
    /** A delta: state plus changed minutes. The store must commit this batch atomically. */
    public record Snapshot(State state, List<Minute> minutes) {
        public Snapshot { minutes = List.copyOf(minutes); }
    }
    /** Complete retained snapshot used for restart recovery; never sum inherited instance snapshots. */
    public record Restored(State state, List<Minute> minutes) {
        public Restored { minutes = List.copyOf(minutes); }
    }
    public record View(State state, List<Minute> minutes) {
        public View { minutes = List.copyOf(minutes); }
    }
    public record Metrics(int subjects, int queuedEvents, long droppedEvents, long storeFailures,
                          long capacityBypasses, boolean recovering) {}
    static void bounded(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException(label + " 不能为空、包含 NUL 或超过 " + max + " 字符");
    }
}

