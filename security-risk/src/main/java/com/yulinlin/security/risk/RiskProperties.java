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
    private String group;
    private Memory memory = new Memory();
    private Storage storage = new Storage();
    private Async async = new Async();
    private Map<String, Behavior> behaviors = new LinkedHashMap<>();
    public enum Mode { OBSERVE, BLOCK }
    public enum OverflowPolicy { DROP_NEWEST }
    @Data public static class Memory {
        private int maximumSize = 10_000;
        private int maximumEventsPerSubject = 20_000;
        private Duration retention = Duration.ofHours(24);
        private boolean restoreOnStartup = true;
    }
    @Data public static class Storage {
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
        private List<String> components = new ArrayList<>(List.of("fast-switch", "active-access"));
        private FastSwitch fastSwitch = new FastSwitch();
        private ActiveAccess activeAccess = new ActiveAccess();
        /** Overrides for custom components. Built-ins have their own mode property. */
        private Map<String, Mode> componentModes = new LinkedHashMap<>();
    }
    @Data public static class FastSwitch {
        private Mode mode;
        private SwitchTier rapid = new SwitchTier(Duration.ofMinutes(5), Duration.ofSeconds(2), 50, 1);
        private SwitchTier normal = new SwitchTier(Duration.ofMinutes(15), Duration.ofSeconds(5), 200, .70);
    }
    @Data public static class SwitchTier {
        private Duration window;
        private Duration gap;
        private int minVideos;
        private double minRatio;
        public SwitchTier() {}
        public SwitchTier(Duration window, Duration gap, int minVideos, double minRatio) {
            this.window = window; this.gap = gap; this.minVideos = minVideos; this.minRatio = minRatio;
        }
    }
    @Data public static class ActiveAccess {
        private Mode mode;
        private Duration window = Duration.ofHours(6);
        private double activeRatio = .90;
        private long minRequests = 60;
    }
    public Mode mode(String behavior, String component) {
        var b = behaviors.get(behavior);
        Mode override = switch (component) {
            case "fast-switch" -> b.fastSwitch.mode;
            case "active-access" -> b.activeAccess.mode;
            default -> b.componentModes.get(component);
        };
        return override == null ? mode : override;
    }
    public boolean requiresControl() {
        return behaviors.entrySet().stream().anyMatch(e -> e.getValue().enabled && e.getValue().components.stream()
                .anyMatch(id -> mode(e.getKey(), id) == Mode.BLOCK));
    }
    public void validate() {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(async.overflowPolicy, "async.overflow-policy");
        positive(memory.retention, "memory.retention");
        if (memory.retention.compareTo(Duration.ofDays(7)) > 0) invalid("memory.retention 最多 7d");
        positive(storage.retention, "storage.retention");
        if (storage.retention.compareTo(memory.retention) < 0) invalid("storage.retention 不能短于 memory.retention");
        positive(storage.cleanupInterval, "storage.cleanup-interval");
        positive(async.flushInterval, "async.flush-interval");
        positive(async.snapshotInterval, "async.snapshot-interval");
        positive(async.retryInterval, "async.retry-interval");
        positive(async.shutdownTimeout, "async.shutdown-timeout");
        if (memory.maximumSize < 1 || memory.maximumEventsPerSubject < 2 || async.batchSize < 1
                || async.queueCapacity < async.batchSize || async.retryCount < 0) invalid("容量或重试参数无效");
        if (behaviors == null || behaviors.isEmpty()) invalid("至少配置一个行为");
        behaviors.forEach((name, b) -> {
            if (name == null || !name.matches("[a-z][a-z0-9-]{0,63}") || b == null) invalid("行为名无效");
            if (b.components == null || b.components.size() > 16) invalid("每个行为最多 16 个组件");
            var ids = new HashSet<String>();
            for (String id : b.components)
                if (id == null || !id.matches("[a-z][a-z0-9-]{0,63}") || !ids.add(id)) invalid("组件 ID 无效或重复");
            b.componentModes.forEach((id, value) -> {
                if (!ids.contains(id) || value == null || id.equals("fast-switch") || id.equals("active-access"))
                    invalid("component-modes 只能配置已选择的自定义组件");
            });
            if (ids.contains("fast-switch")) {
                validateTier(b.fastSwitch.rapid, true); validateTier(b.fastSwitch.normal, false);
            }
            if (ids.contains("active-access")) {
                var a = b.activeAccess;
                positive(a.window, "active-access.window");
                if (a.window.toMillis() % 600_000 != 0 || a.window.compareTo(memory.retention) > 0)
                    invalid("active-access.window 必须为整十分钟且不超过 memory.retention");
                ratio(a.activeRatio, "active-access.active-ratio");
                if (a.minRequests < 1) invalid("active-access.min-requests 必须为正");
            }
        });
    }
    private void validateTier(SwitchTier tier, boolean rapid) {
        positive(tier.window, "fast-switch.window"); positive(tier.gap, "fast-switch.gap");
        if (tier.window.compareTo(memory.retention) > 0 || tier.gap.compareTo(tier.window) > 0)
            invalid("快切 gap <= window <= memory.retention");
        if (tier.minVideos < 1 || tier.minVideos > memory.maximumEventsPerSubject - (rapid ? 1 : 0))
            invalid("快切样本数超出 memory.maximum-events-per-subject");
        ratio(tier.minRatio, "fast-switch.min-ratio");
        if (rapid && tier.minRatio != 1) invalid("极速档按快切视频数判断，不支持 min-ratio");
    }
    private static void ratio(double value, String key) {
        if (!Double.isFinite(value) || value <= 0 || value > 1) invalid(key + " 必须在 (0,1] 内");
    }
    private static void positive(Duration value, String key) {
        if (value == null || value.isNegative() || value.isZero() || value.toMillis() < 1)
            invalid(key + " 必须至少 1ms");
    }
    private static void invalid(String message) { throw new IllegalArgumentException("yulinlin.security.risk: " + message); }
}
