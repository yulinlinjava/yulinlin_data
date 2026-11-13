package com.yulinlin.security.risk;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    /** Trusted historical input; occurredAt is the original event time in epoch milliseconds. */
    public record ReplayEvent(String userId, String behaviorType, String resourceId, String eventId, long occurredAt) {
        public ReplayEvent {
            new BehaviorEvent(userId, behaviorType, resourceId, eventId);
            if (occurredAt < 0) throw new IllegalArgumentException("occurredAt 必须为非负 epoch 毫秒");
        }
        public ReplayEvent(String userId, String behaviorType, String resourceId, long occurredAt) {
            this(userId, behaviorType, resourceId, null, occurredAt);
        }
        public BehaviorEvent event() { return new BehaviorEvent(userId, behaviorType, resourceId, eventId); }
    }
    /** One decision per input row; inputIndex is the zero-based position before sorting. */
    public record ReplayResult(int inputIndex, ReplayEvent event, Decision decision) {
        public ReplayResult {
            if (inputIndex < 0) throw new IllegalArgumentException("inputIndex 必须非负");
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(decision, "decision");
        }
    }
    public enum Status { OBSERVING, ALLOWED, SUSPECTED, BLOCKED, EXEMPT, DISABLED, CAPACITY }
    public enum DetectionStatus { OBSERVING, NORMAL, MATCHED, CAPACITY }
    public record Match(String componentId, String ruleId, long windowStart, long windowEnd,
                        String reason, Map<String, Long> statistics) {
        public Match { statistics = statistics == null ? Map.of() : Map.copyOf(statistics); }
    }
    public record Detection(String componentId, DetectionStatus status, String reason,
                            List<Match> matches, Map<String, Long> statistics) {
        public Detection {
            Objects.requireNonNull(componentId); Objects.requireNonNull(status);
            matches = List.copyOf(matches); statistics = Map.copyOf(statistics);
            if ((status == DetectionStatus.MATCHED) != !matches.isEmpty())
                throw new IllegalArgumentException("MATCHED 必须提供命中依据，其他状态不能携带命中");
            if (matches.stream().anyMatch(m -> !componentId.equals(m.componentId())))
                throw new IllegalArgumentException("命中依据必须属于当前组件");
        }
    }
    /** An effective business restriction; null expiresAt means permanent/unknown expiry. */
    public record Restriction(String id, String reason, Long expiresAt) {
        public Restriction {
            bounded(id, 128, "restriction.id");
            if (expiresAt != null && expiresAt <= 0) throw new IllegalArgumentException("expiresAt 必须为正 epoch 毫秒或 null");
        }
    }
    public record Decision(boolean allowed, Status status, long blockedUntil, List<Match> matches,
                           List<Detection> components, Restriction restriction) {
        public Decision {
            matches = List.copyOf(matches);
            components = components == null ? List.of() : List.copyOf(components);
        }
        public Decision(boolean allowed, Status status, long blockedUntil, List<Match> matches) {
            this(allowed, status, blockedUntil, matches, List.of(), null);
        }
    }
    public record Event(String id, Subject subject, String resourceId, long occurredAt, Decision decision) {}
    /** V2 complete snapshot. restriction is audit metadata, never an authority for live enforcement. */
    public record State(Subject subject, String streamId, String signature, long version, long updatedAt,
                        long exemptUntil, List<Detection> components, Restriction restriction,
                        Map<String, String> detectorStates) {
        public State { components = List.copyOf(components); detectorStates = Map.copyOf(detectorStates); }
    }
    public record Snapshot(State state) {}
    public record Restored(State state) {}
    public record View(State state) {}
    public record Metrics(int subjects, int queuedEvents, long droppedEvents, long storeFailures,
                          long capacityBypasses, boolean recovering) {}
    static void bounded(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException(label + " 不能为空、包含 NUL 或超过 " + max + " 字符");
    }
}

