package com.yulinlin.security.risk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.*;

@Data
@ConfigurationProperties(prefix = "yulinlin.security.risk", ignoreUnknownFields = false)
public class RiskProperties {
    private boolean enabled;
    private Mode mode = Mode.OBSERVE;
    private Duration blockDuration = Duration.ofMinutes(30);
    private Memory memory = new Memory();
    private Storage storage = new Storage();
    private Async async = new Async();
    private Map<String, Behavior> behaviors = new LinkedHashMap<>();
    public enum Mode { OBSERVE, BLOCK }
    public enum OverflowPolicy { DROP_NEWEST }
    @Data public static class Memory {
        private int maximumSize = 10_000;
        private Duration retention = Duration.ofHours(24);
        private boolean restoreOnStartup = true;
    }
    @Data public static class Storage {
        private String group;
        private Duration retention = Duration.ofHours(24);
        private Duration cleanupInterval = Duration.ofHours(1);
    }
    @Data public static class Async {
        private int batchSize = 500;
        private Duration flushInterval = Duration.ofSeconds(1);
        private int queueCapacity = 50_000;
        private OverflowPolicy overflowPolicy = OverflowPolicy.DROP_NEWEST;
        private Duration snapshotInterval = Duration.ofSeconds(5);
        private int retryCount = 3;
        private Duration retryInterval = Duration.ofSeconds(5);
        private Duration shutdownTimeout = Duration.ofSeconds(10);
    }
    @Data public static class Behavior {
        private boolean enabled = true;
        private List<Rule> rules = new ArrayList<>();
    }
    @Data public static class Rule {
        private String id;
        private String type;
        private boolean enabled = true;
        private Duration window = Duration.ofMinutes(120);
        private int minActiveMinutes = 120;
        private long minRequests = 500;
        private Duration shortGap = Duration.ofSeconds(10);
        private double minShortGapRatio = 0.70;
    }
    public void validate() {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(async.getOverflowPolicy(), "async.overflow-policy");
        positive(blockDuration, "block-duration");
        positive(memory.retention, "memory.retention");
        if (memory.retention.compareTo(Duration.ofDays(7)) > 0) invalid("memory.retention 最多 7d");
        positive(storage.retention, "storage.retention");
        if (storage.retention.compareTo(memory.retention) < 0) invalid("storage.retention 不能短于 memory.retention");
        positive(storage.cleanupInterval, "storage.cleanup-interval");
        positive(async.flushInterval, "async.flush-interval");
        positive(async.snapshotInterval, "async.snapshot-interval");
        positive(async.retryInterval, "async.retry-interval");
        positive(async.shutdownTimeout, "async.shutdown-timeout");
        if (memory.maximumSize < 1 || async.batchSize < 1 || async.queueCapacity < async.batchSize || async.retryCount < 0)
            invalid("容量必须为正，queue-capacity >= batch-size，retry-count >= 0");
        if (behaviors == null || behaviors.isEmpty()) invalid("至少配置一个行为");
        behaviors.forEach((name, behavior) -> {
            if (name == null || !name.matches("[a-z][a-z0-9-]{0,63}") || behavior == null)
                invalid("行为名仅支持小写字母、数字和短横线，最多 64 字符");
            if (behavior.rules == null || behavior.rules.size() > 16) invalid("每个行为最多 16 条规则");
            Set<String> ids = new HashSet<>();
            for (Rule rule : behavior.rules) {
                if (rule == null || rule.id == null || !rule.id.matches("[a-zA-Z0-9_-]{1,64}") || !ids.add(rule.id))
                    invalid("规则 id 必须非空且唯一");
                if (rule.type == null || rule.type.isBlank()) invalid("规则 type 不能为空");
                positive(rule.window, "rule.window");
                if (rule.window.toMillis() % 60_000 != 0 || rule.window.compareTo(memory.retention) > 0)
                    invalid("window 必须为整分钟且不超过 memory.retention");
                if (rule.minActiveMinutes < 1 || rule.minActiveMinutes > rule.window.toMinutes())
                    invalid("min-active-minutes 超出窗口");
                positive(rule.shortGap, "short-gap");
                if (rule.minRequests < 2 || !Double.isFinite(rule.minShortGapRatio)
                        || rule.minShortGapRatio < 0 || rule.minShortGapRatio > 1) invalid("密集访问阈值无效");
            }
        });
    }
    private static void positive(Duration value, String key) {
        if (value == null || value.isNegative() || value.isZero() || value.toMillis() < 1)
            invalid(key + " 必须至少 1ms");
    }
    private static void invalid(String message) { throw new IllegalArgumentException("yulinlin.security.risk: " + message); }
}

