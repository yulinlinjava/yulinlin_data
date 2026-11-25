package com.yulinlin.mcp.process;

import com.yulinlin.mcp.McpException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.LongFunction;

/** Stops only the selected process using its native handle; never invokes a shell or walks descendants. */

public class ProcessControlService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProcessControlService.class);
    private final ProcessHandle current;
    private final LongFunction<Optional<ProcessHandle>> lookup;

    public ProcessControlService() { this(ProcessHandle.current(), ProcessHandle::of); }
    ProcessControlService(ProcessHandle current, LongFunction<Optional<ProcessHandle>> lookup) {
        this.current = current; this.lookup = lookup;
    }

    Map<Long, String> protectedProcesses() {
        var protectedIds = new HashMap<Long, String>();
        protectedIds.put(1L, "系统主进程"); protectedIds.put(current.pid(), "本应用");
        var visited = new HashSet<Long>(); visited.add(current.pid());
        var parent = current.parent();
        for (int depth = 0; parent.isPresent(); depth++) {
            var process = parent.get();
            if (depth >= 128 || !visited.add(process.pid())) throw new McpException(503, "无法核对应用父进程，暂不允许停止操作");
            protectedIds.putIfAbsent(process.pid(), "应用父进程"); parent = process.parent();
        }
        return protectedIds;
    }

    public synchronized StopResult stop(long pid, String identity, boolean force, boolean confirm) {
        if (pid <= 0 || !confirm) throw new IllegalArgumentException("请选择有效进程并确认停止操作");
        Instant expected;
        try {
            if (identity == null || identity.length() > 64) throw new IllegalArgumentException("缺少进程身份，请刷新列表");
            expected = Instant.parse(identity);
        } catch (DateTimeParseException error) { throw new IllegalArgumentException("进程身份无效，请刷新列表"); }
        try {
            String protection = protectedProcesses().get(pid);
            if (protection != null) throw new McpException(403, protection + "受保护，不能通过进程面板停止");
            var found = lookup.apply(pid);
            if (found.isEmpty()) return new StopResult(pid, "exited", force);
            var process = found.get();
            if (!process.isAlive()) return new StopResult(pid, "exited", force);
            Instant actual = process.info().startInstant().orElse(null);
            if (actual == null || !actual.equals(expected))
                throw new McpException(409, "进程已变化或无法核对启动时间，请刷新列表，未发送停止请求");
            if (!force && !process.supportsNormalTermination())
                throw new McpException(409, "该进程不支持正常停止，请核对后选择强制结束");
            // Keep this handle through signalling, rather than resolving the PID again after validation.
            boolean accepted = force ? process.destroyForcibly() : process.destroy();
            boolean alive = process.isAlive();
            if (!accepted && alive) throw new McpException(403, "系统拒绝停止进程，请检查运行应用的系统用户权限");
            log.info("进程停止请求 pid={} startedAt={} force={} accepted={}", pid, expected, force, accepted);
            return new StopResult(pid, alive ? "requested" : "exited", force);
        } catch (SecurityException error) {
            throw new McpException(403, "系统拒绝访问该进程，请检查运行应用的系统用户权限");
        } catch (UnsupportedOperationException error) {
            throw new McpException(409, "当前运行环境不支持此进程操作");
        }
    }

    public record StopResult(long pid, String state, boolean force) { }
}
