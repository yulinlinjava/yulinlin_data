package com.yulinlin.mcp.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.data.DataValues;
import com.yulinlin.mcp.data.McpDataScope;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * GraalJS 沙箱（SandboxPolicy.CONSTRAINED）：不能访问 Java 类、文件、网络、线程、本地代码，
 * 只能调用注入的 query / queryOne / execute / log。数据以 JSON 字符串进出沙箱，不暴露任何 Java 对象。
 * 超时由守护线程强制关闭上下文。
 */
public class ScriptSandbox implements AutoCloseable {
    public static final int MAX_SCRIPT_CHARS = 64 * 1024;
    public static final int MAX_OUTPUT_CHARS = 1024 * 1024;
    static final int MAX_LOGS = 100;
    static final int MAX_LOG_CHARS = 2000;

    private final ObjectMapper json;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("mcp-script-watchdog").factory());

    public ScriptSandbox(ObjectMapper json) { this.json = json; }

    public record Output(JsonNode result, List<String> logs) { }

    public Output run(String script, McpDataScope scope, long timeoutMillis) {
        if (script == null || script.isBlank()) throw new IllegalArgumentException("script 不能为空");
        if (script.length() > MAX_SCRIPT_CHARS) throw new IllegalArgumentException("script 最长 " + MAX_SCRIPT_CHARS + " 字符");
        var logs = new ArrayList<String>();
        try (Context context = Context.newBuilder("js")
                .sandbox(SandboxPolicy.CONSTRAINED)
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            context.getBindings("js").putMember("__host", host(scope, logs));
            ScheduledFuture<?> timer = watchdog.schedule(() -> context.close(true), timeoutMillis, TimeUnit.MILLISECONDS);
            try {
                Value value = context.eval(Source.newBuilder("js", wrap(script), "analyze.js").buildLiteral());
                String text = value.isNull() ? "null" : value.asString();
                if (text.length() > MAX_OUTPUT_CHARS)
                    throw new IllegalArgumentException("返回结果超过 1 MB，请在脚本里进一步聚合，只返回需要的结论数据");
                return new Output(json.readTree(text), List.copyOf(logs));
            } catch (PolyglotException e) {
                throw translate(e, timeoutMillis);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("脚本结果无法解析", e);
            } finally {
                timer.cancel(false);
            }
        }
    }

    private RuntimeException translate(PolyglotException e, long timeoutMillis) {
        if (e.isCancelled()) return new IllegalArgumentException("脚本执行超过 " + timeoutMillis / 1000 + " 秒，已终止" );
        if (e.isHostException()) {
            Throwable host = e.asHostException();
            if (host instanceof RuntimeException runtime) return runtime;
            return new IllegalStateException("脚本调用失败", host);
        }
        if (e.isResourceExhausted()) return new IllegalArgumentException("脚本占用资源过多（如无限递归），已终止");
        String where = "";
        var location = e.getSourceLocation();
        if (location != null && location.getStartLine() > PRELUDE_LINES)
            where = "（脚本第 " + (location.getStartLine() - PRELUDE_LINES) + " 行）";
        return new IllegalArgumentException("脚本错误" + where + ": " + e.getMessage());
    }

    private ProxyObject host(McpDataScope scope, List<String> logs) {
        var members = new HashMap<String, Object>();
        members.put("query", (ProxyExecutable) a -> write(scope.query(str(a, 0), str(a, 1), params(a, 2))));
        members.put("queryOne", (ProxyExecutable) a -> write(scope.queryOne(str(a, 0), str(a, 1), params(a, 2))));
        members.put("execute", (ProxyExecutable) a -> scope.execute(str(a, 0), str(a, 1), params(a, 2)));
        members.put("log", (ProxyExecutable) a -> {
            if (logs.size() < MAX_LOGS) {
                String line = str(a, 0);
                logs.add(line.length() > MAX_LOG_CHARS ? line.substring(0, MAX_LOG_CHARS) + "…" : line);
            }
            return null;
        });
        return ProxyObject.fromMap(members);
    }

    private static String str(Value[] args, int index) {
        if (args.length <= index || args[index].isNull()) throw new IllegalArgumentException("缺少第 " + (index + 1) + " 个参数");
        return args[index].asString();
    }

    private Map<String, Object> params(Value[] args, int index) {
        if (args.length <= index || args[index].isNull()) return Map.of();
        try { return DataValues.params(json.readTree(args[index].asString())); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("参数无法转换为 JSON"); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    /** 前置代码的行数，用来把错误行号换算回用户脚本的行号。 */
    static final int PRELUDE_LINES = 10;

    static String wrap(String script) {
        return """
                (() => {
                "use strict";
                const __h = __host;
                const __p = p => JSON.stringify(p === undefined ? {} : p);
                const __c = c => typeof c === "string" ? c : JSON.stringify(c);
                const query = (group, command, params) => JSON.parse(__h.query(String(group), __c(command), __p(params)));
                const queryOne = (group, command, params) => JSON.parse(__h.queryOne(String(group), __c(command), __p(params)));
                const execute = (group, sql, params) => __h.execute(String(group), String(sql), __p(params));
                const log = (...args) => { __h.log(args.map(a => typeof a === "string" ? a : JSON.stringify(a)).join(" ")); };
                const __result = (() => {
                """ + script + """

                })();
                return JSON.stringify(__result === undefined ? null : __result);
                })()
                """;
    }

    @Override
    public void close() { watchdog.shutdownNow(); }
}
