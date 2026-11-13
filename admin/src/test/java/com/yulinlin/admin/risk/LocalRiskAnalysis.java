package com.yulinlin.admin.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.risk.RiskModels.*;
import com.yulinlin.security.risk.RiskProperties;
import com.yulinlin.security.risk.RiskReplay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** User-level evidence report. Replays each subject independently so a busy site cannot evict other users. */
final class LocalRiskAnalysis {
    enum Finding { SUSPECTED, INCOMPLETE, OBSERVING, NOT_MATCHED }
    record Hit(String componentId, String ruleId, long occurredAt, String reason, Map<String, Long> statistics) {}
    record User(String userId, String behaviorType, int records, long distinctResources, long firstEventAt,
                long lastEventAt, Finding finding, boolean incomplete, Long firstHitAt, List<Hit> hits) {}
    record Report(String start, String end, String zone, int records, int users, long suspectedUsers,
                  long incompleteUsers, Map<String, RiskProperties.Behavior> rules, List<User> results) {}
    record FilesWritten(Path json, Path markdown) {}
    record Output(String start, String end, String zone, int records, int users, long suspectedUsers,
                  long incompleteUsers, Integer reportRequestsGreaterThan, int listedUsers,
                  Map<String, RiskProperties.Behavior> rules, List<User> results) {}

    static Report analyze(LocalRiskHistory.Loaded history, RiskProperties properties) {
        var grouped = new LinkedHashMap<Subject, List<ReplayEvent>>();
        for (var event : history.events())
            grouped.computeIfAbsent(new Subject(event.userId(), event.behaviorType()), ignored -> new ArrayList<>()).add(event);
        var replay = new RiskReplay(properties);
        var users = new ArrayList<User>();
        grouped.forEach((subject, events) -> {
            var results = replay.replay(events);
            var hits = new LinkedHashMap<String, Hit>();
            boolean incomplete = false;
            for (var row : results) {
                incomplete |= row.decision().status() == Status.CAPACITY
                        || row.decision().components().stream().anyMatch(d -> d.status() == DetectionStatus.CAPACITY);
                for (var match : row.decision().matches())
                    hits.putIfAbsent(match.componentId() + "/" + match.ruleId(), new Hit(match.componentId(),
                            match.ruleId(), row.event().occurredAt(), match.reason(), match.statistics()));
            }
            var last = results.getLast().decision();
            Finding finding = !hits.isEmpty() ? Finding.SUSPECTED : incomplete ? Finding.INCOMPLETE
                    : last.status() == Status.ALLOWED ? Finding.NOT_MATCHED : Finding.OBSERVING;
            users.add(new User(subject.userId(), subject.behaviorType(), events.size(),
                    events.stream().map(ReplayEvent::resourceId).filter(Objects::nonNull).distinct().count(),
                    results.getFirst().event().occurredAt(), results.getLast().event().occurredAt(), finding, incomplete,
                    hits.isEmpty() ? null : hits.values().iterator().next().occurredAt(), List.copyOf(hits.values())));
        });
        users.sort(Comparator.comparing(User::finding).thenComparing(User::firstHitAt, Comparator.nullsLast(Long::compareTo))
                .thenComparing(User::userId).thenComparing(User::behaviorType));
        var range = history.range();
        return new Report(range.start().toString(), range.end().toString(), range.zone().getId(), history.events().size(),
                users.size(), users.stream().filter(u -> u.finding() == Finding.SUSPECTED).count(),
                users.stream().filter(User::incomplete).count(), Map.copyOf(properties.getBehaviors()), List.copyOf(users));
    }

    static FilesWritten write(Report report, Path directory) throws IOException {
        return write(report, directory, null);
    }

    static FilesWritten write(Report report, Path directory, Integer requestsGreaterThan) throws IOException {
        var selected = select(report, requestsGreaterThan);
        Files.createDirectories(directory);
        Path json = directory.resolve("risk-analysis.json").toAbsolutePath().normalize();
        Path markdown = directory.resolve("risk-analysis.md").toAbsolutePath().normalize();
        var output = new Output(report.start(), report.end(), report.zone(), report.records(), report.users(),
                report.suspectedUsers(), report.incompleteUsers(), requestsGreaterThan, selected.size(), report.rules(), selected);
        new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter().writeValue(json.toFile(), output);
        var zone = ZoneId.of(report.zone());
        var text = new StringBuilder("# 今日历史行为分析\n\n")
                .append("范围：").append(report.start()).append(" 至 ").append(report.end()).append("（")
                .append(report.zone()).append("，结束时间不包含）。\n\n")
                .append("共 ").append(report.records()).append(" 条记录、").append(report.users()).append(" 个用户；")
                .append("疑似机器人 ").append(report.suspectedUsers()).append(" 个，统计不完整 ")
                .append(report.incompleteUsers()).append(" 个。\n\n");
        if (requestsGreaterThan != null)
            text.append("本报告仅列出命中风控且范围内请求记录数 **> ").append(requestsGreaterThan)
                    .append("** 的用户，共 **").append(selected.size()).append("** 人；等于阈值的不列出。\n\n");
        text.append("结论按当前规则给出；未命中或观察不足不代表已确认正常。历史回放不会封禁用户。")
                .append("规则配置及每个用户的依据见同目录 JSON。\n\n")
                .append("| 用户 | 记录数 | 不同视频 | 结果 | 首次命中 | 命中规则 |\n")
                .append("| --- | ---: | ---: | --- | --- | --- |\n");
        for (var user : selected) {
            String rules = String.join("、", user.hits().stream().map(hit -> hit.componentId() + "/" + hit.ruleId()).toList());
            text.append("| ").append(cell(user.userId())).append(" | ").append(user.records()).append(" | ")
                    .append(user.distinctResources()).append(" | ").append(label(user.finding()))
                    .append(user.incomplete() ? "（统计不完整）" : "").append(" | ")
                    .append(user.firstHitAt() == null ? "—" : time(user.firstHitAt(), zone)).append(" | ")
                    .append(cell(rules)).append(" |\n");
        }
        for (var user : selected) if (!user.hits().isEmpty()) {
            text.append("\n## 用户 ").append(cell(user.userId())).append("\n\n");
            for (var hit : user.hits()) text.append("- ").append(time(hit.occurredAt(), zone)).append("：")
                    .append(cell(hit.componentId())).append(" / ").append(cell(hit.ruleId())).append("；")
                    .append(cell(hit.reason())).append("；首次命中统计：").append(cell(hit.statistics().toString())).append("。\n");
        }
        Files.writeString(markdown, text.toString(), StandardCharsets.UTF_8);
        return new FilesWritten(json, markdown);
    }

    static void print(Report report, FilesWritten files) {
        print(report, files, null);
    }

    static void print(Report report, FilesWritten files, Integer requestsGreaterThan) {
        System.out.printf("%n全部历史加载完成：%s 至 %s（%s）%n记录=%d 用户=%d 疑似机器人=%d 统计不完整=%d%n",
                report.start(), report.end(), report.zone(), report.records(), report.users(), report.suspectedUsers(), report.incompleteUsers());
        var selected = select(report, requestsGreaterThan);
        if (requestsGreaterThan != null)
            System.out.printf("仅列出命中且请求次数 > %d 的用户：%d 人%n", requestsGreaterThan, selected.size());
        for (var user : selected) if (user.finding() == Finding.SUSPECTED)
            System.out.printf("疑似机器人：用户=%s 记录=%d 不同视频=%d 首次命中=%s 规则=%s%n", user.userId(),
                    user.records(), user.distinctResources(), time(user.firstHitAt(), ZoneId.of(report.zone())),
                    user.hits().stream().map(h -> h.componentId() + "/" + h.ruleId()).toList());
        System.out.printf("分析报告：%s%nJSON 报告：%s%n", files.markdown(), files.json());
    }

    private static List<User> select(Report report, Integer requestsGreaterThan) {
        if (requestsGreaterThan == null) return report.results();
        if (requestsGreaterThan < 0) throw new IllegalArgumentException("请求次数筛选阈值不能为负数");
        return report.results().stream().filter(user -> user.finding() == Finding.SUSPECTED
                        && user.records() > requestsGreaterThan)
                .sorted(Comparator.comparingInt(User::records).reversed().thenComparing(User::userId)).toList();
    }

    private static String label(Finding finding) {
        return switch (finding) {
            case SUSPECTED -> "疑似机器人";
            case INCOMPLETE -> "统计不完整";
            case OBSERVING -> "观察不足";
            case NOT_MATCHED -> "未命中";
        };
    }
    private static String time(long millis, ZoneId zone) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(Instant.ofEpochMilli(millis).atZone(zone));
    }
    private static String cell(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("|", "&#124;").replace("\n", " ").replace("\r", " ").replace("`", "&#96;");
    }
}
