package com.yulinlin.mcp.process;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Bounded ps and /proc snapshot, including command parameters; environment variables are never read. */

public class ProcessOverviewService {
    private static final int LIMIT = 10_000, PAGE_SIZE = 50;
    private Map<Long, CpuSample> previous = Map.of();
    private List<Row> cached = List.of();
    private long sampledNanos;
    private long sampledAt;
    private boolean truncated;
    private final ProcessControlService controls;
    public ProcessOverviewService(ProcessControlService controls) { this.controls = controls; }
    public synchronized void invalidate() { sampledNanos = 0; }

    public synchronized Page list(String query, int page, String sort) {
        if (query == null || query.length() > 100 || page < 1 || page > 200 || sort == null || !Set.of("pid", "memory", "cpu").contains(sort))
            throw new IllegalArgumentException("进程查询参数无效");
        long now = System.nanoTime();
        if (sampledNanos == 0 || now - sampledNanos >= 2_000_000_000L) sample(now);
        String filter = query.strip().toLowerCase(Locale.ROOT);
        Comparator<Row> order = switch (sort) {
            case "memory" -> Comparator.comparing(Row::memoryBytes, Comparator.nullsLast(Comparator.reverseOrder()));
            case "cpu" -> Comparator.comparing(Row::cpuPercent, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparingLong(Row::pid).reversed();
        };
        var matches = cached.stream().filter(row -> filter.isEmpty() || Long.toString(row.pid()).contains(filter)
                || matches(row, filter)).sorted(order.thenComparingLong(Row::pid)).toList();
        int totalPages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, totalPages); int from = (page - 1) * PAGE_SIZE;
        return new Page(matches.subList(from, Math.min(from + PAGE_SIZE, matches.size())), page, totalPages, matches.size(), sampledAt, truncated);
    }

    private void sample(long now) {
        var samples = new HashMap<Long, CpuSample>(); var rows = new ArrayList<Row>();
        var protectedIds = controls.protectedProcesses();
        long currentPid = ProcessHandle.current().pid(), wallTime = System.currentTimeMillis();
        try (var stream = ProcessHandle.allProcesses()) {
            var handles = stream.limit(LIMIT + 1L).toList(); truncated = handles.size() > LIMIT;
            var commands = ProcessPsReader.read();
            for (var handle : handles.stream().limit(LIMIT).toList()) {
                try {
                    if (!handle.isAlive()) continue;
                    var info = handle.info(); Instant started = info.startInstant().orElse(null);
                    Long cpu = info.totalCpuDuration().map(java.time.Duration::toNanos).orElse(null);
                    Double percent = null; var old = previous.get(handle.pid());
                    if (cpu != null && started != null) {
                        if (old != null && old.started().equals(started) && cpu >= old.cpuNanos() && now > old.atNanos())
                            percent = Math.round((cpu - old.cpuNanos()) * 10_000.0 / (now - old.atNanos())) / 100.0;
                        samples.put(handle.pid(), new CpuSample(started, cpu, now));
                    }
                    String name = info.command().map(value -> Path.of(value).getFileName().toString()).orElseGet(() -> name(handle.pid()));
                    var details = ProcessDetailsReader.read(handle, info, name, commands.get(handle.pid()));
                    if (!handle.isAlive() || (started != null && !started.equals(handle.info().startInstant().orElse(null)))) continue;
                    String blocked = protectedIds.get(handle.pid());
                    if (blocked == null && started == null) blocked = "无法核对启动时间";
                    rows.add(new Row(handle.pid(), name, started == null ? null : started.toEpochMilli(),
                            started == null ? null : Math.max(0L, (wallTime - started.toEpochMilli()) / 1000),
                            percent, memory(handle.pid()), handle.pid() == currentPid,
                            blocked == null ? started.toString() : null, blocked, details.application(), details.executable(),
                            details.user(), details.parentPid(), details.workingDirectory(), details.launchTarget(),
                            details.commandLine(), details.commandTruncated()));
                } catch (RuntimeException ignored) { /* Process exit / permissions race: skip, never broaden privileges. */ }
            }
        }
        previous = samples; cached = List.copyOf(rows); sampledNanos = now; sampledAt = wallTime;
    }
    private static String name(long pid) {
        try { return Files.readString(Path.of("/proc", Long.toString(pid), "comm")).strip(); }
        catch (IOException error) { return "不可读取"; }
    }
    private static Long memory(long pid) {
        try (var lines = Files.lines(Path.of("/proc", Long.toString(pid), "status"))) {
            return lines.filter(line -> line.startsWith("VmRSS:"))
                    .map(line -> Long.parseLong(line.substring(6).trim().split("\\s+")[0]) * 1024).findFirst().orElse(null);
        } catch (IOException | RuntimeException error) { return null; }
    }
    private record CpuSample(Instant started, long cpuNanos, long atNanos) { }
    private static boolean matches(Row row, String filter) {
        return java.util.stream.Stream.of(row.name(), row.application(), row.executable(), row.user(), row.workingDirectory(),
                row.launchTarget(), row.commandLine(), row.parentPid() == null ? null : row.parentPid().toString())
                .filter(Objects::nonNull).anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(filter));
    }
    public record Row(long pid, String name, Long startedAt, Long runningSeconds, Double cpuPercent, Long memoryBytes,
                      boolean netdisk, String stopIdentity, String stopBlockedReason, String application, String executable,
                      String user, Long parentPid, String workingDirectory, String launchTarget, String commandLine, boolean commandTruncated) { }
    public record Page(List<Row> items, int page, int totalPages, int total, long sampledAt, boolean truncated) { }
}
