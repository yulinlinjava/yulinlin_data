package com.yulinlin.admin.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.risk.RiskModels.Subject;
import com.yulinlin.security.risk.RiskProperties;
import com.yulinlin.security.risk.RiskReplay;
import com.yulinlin.security.risk.RiskReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in review of the users already selected by the compact report; no whole-site reread. */
class AdminRiskReportTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    record Comparison(String userId, int previousRequests, int reloadedRequests, boolean firstHitsReproduced) {}
    record UiReports(long from, long to, String zoneId, int reportRequestsGreaterThan,
                     List<Comparison> comparisons, List<RiskReport> users) {}

    @Test @DisplayName("按已保存名单生成页面 JSON：快切、活跃分桶、峰值和原始命中依据")
    void exportSelectedUserReports() throws Exception {
        String source = System.getProperty("risk.report.source");
        assumeTrue(source != null && !source.isBlank(), "设置 -Drisk.report.source 指向 risk-analysis.json，才连接历史库");
        String configuration = System.getProperty("risk.replay.config");
        assertNotNull(configuration, "同时设置 -Drisk.replay.config 指向本地数据源配置");
        var baseline = JSON.readTree(Path.of(source).toFile());
        var config = LocalRiskHistory.load(new FileSystemResource(configuration));
        ZoneId zone = ZoneId.of(baseline.get("zone").asText());
        String start = baseline.get("start").asText(), end = baseline.get("end").asText();
        long from = LocalDateTime.parse(start).atZone(zone).toInstant().toEpochMilli();
        long to = LocalDateTime.parse(end).atZone(zone).toInstant().toEpochMilli();
        config.history().setStart(start); config.history().setEnd(end); config.history().setZone(zone.getId());
        // Reproduce the saved rules even if local tuning has changed since the earlier run.
        config.risk().setBehaviors(JSON.convertValue(baseline.get("rules"), new TypeReference<Map<String, RiskProperties.Behavior>>() {}));
        var reports = new ArrayList<RiskReport>();
        var comparisons = new ArrayList<Comparison>();
        for (JsonNode user : baseline.withArray("results")) {
            if (!"SUSPECTED".equals(user.get("finding").asText()) || user.get("records").asInt() <= 300) continue;
            String id = user.get("userId").asText(), behavior = user.get("behaviorType").asText();
            config.history().setUserId(id); config.history().setBehaviorType(behavior);
            var events = LocalRiskHistory.read(config);
            var report = new RiskReplay(config.risk()).report(new Subject(id, behavior), from, to, zone, events);
            boolean reproduced = report.hits().size() == user.withArray("hits").size();
            for (var previous : user.withArray("hits")) {
                var hit = report.hits().stream().filter(h -> h.componentId().equals(previous.get("componentId").asText())
                        && h.ruleId().equals(previous.get("ruleId").asText())).findFirst();
                reproduced &= hit.isPresent() && hit.get().firstMatchedAt() == previous.get("occurredAt").asLong()
                        && hit.get().statistics().equals(JSON.convertValue(previous.get("statistics"),
                                new TypeReference<Map<String, Long>>() {}));
            }
            reports.add(report);
            comparisons.add(new Comparison(id, user.get("records").asInt(), events.size(), reproduced));
            System.out.printf("页面报告：用户=%s 请求=%d 原始命中复现=%s%n", id, events.size(), reproduced);
        }
        assertFalse(reports.isEmpty(), "保存的报告中没有命中且请求次数 >300 的用户");
        var output = Path.of(source).toAbsolutePath().getParent();
        var bundle = new UiReports(from, to, zone.getId(), 300, List.copyOf(comparisons), List.copyOf(reports));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("risk-ui-reports.json").toFile(), bundle);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("risk-ui-report-example.json").toFile(), reports.getFirst());
        Files.writeString(output.resolve("risk-ui-evidence.md"), markdown(bundle), StandardCharsets.UTF_8);
        System.out.printf("页面 JSON：%s%n单用户接口示例：%s%n详细依据：%s%n", output.resolve("risk-ui-reports.json"),
                output.resolve("risk-ui-report-example.json"), output.resolve("risk-ui-evidence.md"));
    }

    private static String markdown(UiReports reports) {
        var zone = ZoneId.of(reports.zoneId());
        var text = new StringBuilder("# 用户行为报告与误判复核\n\n范围：")
                .append(time(reports.from(), zone)).append(" 至 ").append(time(reports.to(), zone)).append("（")
                .append(reports.zoneId()).append("）。仅复核原名单中命中且请求次数 >300 的用户。\n\n")
                .append("下表快切次数是相邻不同视频的请求切换次数，占比的分母是不同视频切换总次数；它与命中窗口按不同目标视频去重的占比不同。")
                .append("连续访问按相邻请求间隔超过五分钟断开，仅为请求序列跨度，不代表实际观看时长。\n\n")
                .append("| 用户 | 请求 | <2秒次数/占比 | ≤5秒次数/占比 | 最长连续快切（≤5秒） | 最长访问段 | 最佳完整6h覆盖 |\n")
                .append("| --- | ---: | --- | --- | ---: | --- | --- |\n");
        for (var report : reports.users()) {
            var s = report.switches(); var active = report.bestActiveWindow();
            text.append("| ").append(report.subject().userId()).append(" | ").append(report.summary().requestCount())
                    .append(" | ").append(s.rapid().count()).append("/").append(s.differentResourceSwitches()).append("，").append(percent(s.rapid().ratio()))
                    .append(" | ").append(s.normal().count()).append("/").append(s.differentResourceSwitches()).append("，").append(percent(s.normal().ratio()))
                    .append(" | ").append(s.normal().longestRun()).append(" | ").append(duration(report.activity().longestVisit().durationMillis()))
                    .append(" | ").append(active == null ? "未形成完整观察窗口" : active.activeBuckets() + "/" + active.totalBuckets() + "，" + percent(active.activeRatio()))
                    .append(" |\n");
        }
        text.append("\n## 首次命中窗口\n\n极速档为5分钟内<2秒的不同目标视频≥50，无占比门槛；普通档为15分钟内不同视频≥200且≤5秒快切目标视频占比≥70%；全天候为完整6小时、十分钟桶覆盖≥90%且请求≥60。\n\n");
        for (var report : reports.users()) {
            text.append("### ").append(report.subject().userId()).append("\n\n");
            for (var hit : report.hits()) {
                text.append("- ").append(hit.componentId()).append("/").append(hit.ruleId()).append("：")
                        .append(time(hit.firstMatchedAt(), zone)).append(" 首次命中；窗口 ")
                        .append(time(hit.windowStart(), zone)).append(" 至 ").append(time(hit.windowEnd(), zone)).append("。\n");
                if (hit.fastSwitch() != null) {
                    var fast = hit.fastSwitch();
                    text.append("  去重快切目标视频 ").append(fast.distinctFastVideos()).append("/").append(fast.distinctVideos())
                            .append("，占比 ").append(percent(fast.fastVideoRatio())).append("；原始快切次数 ").append(fast.fastSwitchCount())
                            .append("/").append(fast.differentResourceSwitches()).append("，占比 ").append(percent(fast.fastSwitchRatio()))
                            .append("；同一时间戳的不同视频切换 ").append(fast.sameTimestampSwitches()).append(" 次。\n");
                }
                if (hit.activeAccess() != null) {
                    var active = hit.activeAccess();
                    text.append("  六小时 ").append(active.requestCount()).append(" 次请求；活跃桶 ").append(active.activeBuckets())
                            .append("/").append(active.totalBuckets()).append("，覆盖 ").append(percent(active.activeRatio()))
                            .append("；实际有请求的分钟 ").append(active.activeMinutes()).append("/360；窗口内最长无请求间隔（含边界） ")
                            .append(duration(active.longestGapMillis())).append("。\n");
                }
            }
            var activity = report.activity();
            text.append("\n该范围有请求的分钟：").append(activity.activeMinutes()).append("；最长每分钟都有记录：")
                    .append(activity.longestConsecutiveMinutes()).append(" 分钟；每分钟峰值：").append(activity.peakMinuteRequests())
                    .append("；连续五个自然分钟峰值：").append(activity.peakFiveMinuteRequests()).append("。\n\n");
        }
        text.append("## 数据复现\n\n");
        for (var comparison : reports.comparisons()) text.append("- ").append(comparison.userId()).append("：原记录 ")
                .append(comparison.previousRequests()).append("，重读 ").append(comparison.reloadedRequests())
                .append("，首次命中复现 ").append(comparison.firstHitsReproduced()).append("。\n");
        return text.toString();
    }
    private static String percent(Double ratio) { return ratio == null ? "—" : String.format(Locale.ROOT, "%.1f%%", ratio * 100); }
    private static String duration(long millis) { return millis / 3_600_000 + "小时" + millis / 60_000 % 60 + "分" + millis / 1000 % 60 + "秒"; }
    private static String time(long at, ZoneId zone) { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(Instant.ofEpochMilli(at).atZone(zone)); }
}
