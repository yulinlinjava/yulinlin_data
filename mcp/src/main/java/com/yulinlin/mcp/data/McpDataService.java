package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yulinlin.data.core.cache.CacheRegistry;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.request.TypedRow;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.McpLifecycle;
import com.yulinlin.mcp.McpProperties;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.mcp.tool.McpCallContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * AI 读写业务数据的执行引擎。所有操作都走框架的 {@link RouteSession} 和原生命令 {@link CommandNode}，
 * 与业务代码使用同一套数据源、路由、过滤器、执行日志和编解码：
 * <ul>
 *   <li>JDBC 读：在 {@code RouteSession.transaction(…, 超时)} 中执行，结束时一律回滚；
 *       结果类型为 {@link TypedRow}，保留数字、日期等原始类型和 NULL 列；{@code CommandNode.maxRows} 在驱动层限制读取行数。</li>
 *   <li>Elasticsearch 读（只读）：原生只读命令，不参与事务；条数上限和服务端超时写进命令里（见 {@link CommandGuard}）。</li>
 *   <li>写（仅 JDBC）：一个带超时的框架事务，预演（dryRun）或影响行数与 expectAffectedRows 不符时回滚，否则提交。</li>
 *   <li>写事务进行中读取其他 JDBC 数据源时，该数据源登记到同一个框架事务（多数据源事务）；Elasticsearch 没有事务，直接执行。</li>
 * </ul>
 */
public class McpDataService implements McpLifecycle {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpDataService.class);
    /** 返回给 AI 的单元格最长字符数。 */
    static final int MAX_CELL_CHARS = 2000;
    /** 沙箱脚本内部：所有查询合计最多读取的行数，以及单元格最长字符数（数据不直接返回给 AI）。 */
    public static final int SCRIPT_ROW_BUDGET = 200_000;
    static final int SCRIPT_CELL_CHARS = 65_536;
    private static final int MAX_STATEMENTS = 100;
    private static final String REQUEST_KIND = "data-request";

    private final McpProperties properties;
    private final McpStore store;
    private final ObjectMapper json;
    private final Supplier<CacheRegistry> caches;
    private final Semaphore slots = new Semaphore(8);
    private volatile Map<String, Source> sources;
    private final Map<String, String> products = new ConcurrentHashMap<>();

    public McpDataService(McpProperties properties, McpStore store, ObjectMapper json, Supplier<CacheRegistry> caches) {
        this.properties = properties;
        this.store = store;
        this.json = json;
        this.caches = caches == null ? () -> null : caches;
    }

    // ------------------------------------------------------------------ 配置与数据源

    /** 开放给 AI 的数据源。writable 只可能是 JDBC。 */
    public record Source(String group, DataKind kind, boolean writable) { }

    /** 配置了 write 时注册 data.execute；不需要访问数据源，工具注册阶段即可调用。 */
    public boolean writeEnabled() { return !properties.getData().getWrite().isEmpty(); }

    public int maxRows() { return properties.getData().getMaxRows(); }

    public int timeoutSeconds() { return properties.getData().getTimeoutSeconds(); }

    @Override
    public void initialize() { sources(); }

    /** 第一次使用时按配置和项目实际注册的数据源解析，之后缓存。 */
    public Map<String, Source> sources() {
        var resolved = sources;
        if (resolved != null) return resolved;
        synchronized (this) {
            if (sources == null) sources = resolve();
            return sources;
        }
    }

    private Map<String, Source> resolve() {
        RouteSession route = SessionUtil.route();
        if (route == null) throw new IllegalStateException("MCP 数据工具需要先初始化 ORM 数据源");
        var known = new TreeSet<>(route.loadBalanceList());
        var config = properties.getData();
        var write = new LinkedHashSet<>(config.getWrite());
        boolean all = config.getRead().contains("*");
        var wanted = new LinkedHashSet<String>();
        if (all) wanted.addAll(known);
        config.getRead().stream().filter(g -> !g.equals("*")).forEach(wanted::add);
        wanted.addAll(write);
        var result = new LinkedHashMap<String, Source>();
        for (String group : wanted) {
            boolean explicit = !all || config.getRead().contains(group) || write.contains(group);
            if (!known.contains(group))
                throw new IllegalStateException("yulinlin.mcp.data 中的数据源组不存在: " + group + "，项目中的数据源组: " + known);
            DataKind kind = DataKind.of(route.session(group));
            if (kind == null) {
                if (explicit) throw new IllegalStateException("数据源组 " + group + " 的类型不受 MCP 数据工具支持（支持 JDBC、Elasticsearch）");
                continue;
            }
            if (write.contains(group) && kind != DataKind.JDBC)
                throw new IllegalStateException("yulinlin.mcp.data.write 只支持 JDBC 数据源，" + group + " 是 " + kind.label());
            result.put(group, new Source(group, kind, write.contains(group)));
        }
        return Collections.unmodifiableMap(result);
    }

    private Source source(String group, boolean write) {
        var all = sources();
        var source = group == null ? null : all.get(group);
        if (source == null) throw new IllegalArgumentException("未开放的数据源: " + group + "，可用: " + all.keySet() + "（data.sources 可查看类型）");
        if (write && !source.writable())
            throw new IllegalArgumentException("数据源 " + group + " 只读，可写的数据源: "
                    + all.values().stream().filter(Source::writable).map(Source::group).toList());
        return source;
    }

    // ------------------------------------------------------------------ data.sources / data.schema

    public Map<String, Object> describeSources() {
        var list = new ArrayList<Map<String, Object>>();
        for (var s : sources().values()) {
            var m = new LinkedHashMap<String, Object>();
            m.put("group", s.group());
            m.put("type", s.kind().label());
            m.put("writable", s.writable());
            if (s.kind() == DataKind.JDBC) m.put("database", product(s.group()));
            list.add(m);
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("sources", list);
        out.put("maxRows", maxRows());
        out.put("timeoutSeconds", timeoutSeconds());
        return out;
    }

    private String product(String group) {
        return products.computeIfAbsent(group, g -> {
            try { return JdbcMetadata.product(g); }
            catch (RuntimeException e) { log.warn("读取数据库产品名失败 group={}", g, e); return "unknown"; }
        });
    }

    /** 不传 table 列出表 / 索引；传了返回名称包含它的对象（最多 20 个）的字段。 */
    public Map<String, Object> schema(String group, String table) {
        var source = source(group, false);
        return guarded(() -> {
            var out = new LinkedHashMap<String, Object>();
            out.put("group", group);
            out.put("type", source.kind().label());
            out.putAll(switch (source.kind()) {
                case JDBC -> translate(() -> JdbcMetadata.schema(group, table));
                case ELASTICSEARCH -> esSchema(group, table);
            });
            return out;
        });
    }

    private Map<String, Object> esSchema(String group, String table) {
        long deadline = deadline();
        var rows = nativeRows(group, "{\"mapping\": \"*\"}", Map.of(), deadline);
        var out = new LinkedHashMap<String, Object>();
        String needle = table == null || table.isBlank() ? null : table.strip().toLowerCase(Locale.ROOT);
        var indices = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            String index = String.valueOf(row.get("index"));
            if (needle != null && !index.toLowerCase(Locale.ROOT).contains(needle)) continue;
            var fields = new LinkedHashMap<String, Object>();
            if (row.get("mappings") instanceof Map<?, ?> mappings) flattenMapping(mappings.get("properties"), "", fields);
            var m = new LinkedHashMap<String, Object>();
            m.put("name", index);
            if (needle == null) m.put("fieldCount", fields.size());
            else m.put("fields", fields);
            indices.add(m);
            if (needle != null && indices.size() >= 20) break;
            if (indices.size() >= 1000) break;
        }
        out.put("indices", indices);
        if (needle == null) out.put("hint", "传入 table 参数（索引名的一部分）查看字段和类型");
        else if (indices.isEmpty()) out.put("hint", "没有名称包含 " + table + " 的索引，不传 table 可查看全部索引");
        return out;
    }

    private static void flattenMapping(Object properties, String prefix, Map<String, Object> out) {
        if (!(properties instanceof Map<?, ?> props)) return;
        for (var e : props.entrySet()) {
            if (out.size() >= 2000) return;
            String path = prefix + e.getKey();
            if (!(e.getValue() instanceof Map<?, ?> field)) continue;
            Object type = field.get("type");
            if (type != null) out.put(path, type);
            else if (field.containsKey("properties")) out.put(path, "object");
            flattenMapping(field.get("properties"), path + ".", out);
            if (field.get("fields") instanceof Map<?, ?> multi)
                for (var m : multi.entrySet())
                    if (m.getValue() instanceof Map<?, ?> sub && sub.get("type") != null) out.put(path + "." + m.getKey(), sub.get("type"));
        }
    }

    // ------------------------------------------------------------------ data.query

    public record QueryResult(List<String> columns, List<Map<String, Object>> rows, boolean truncated) { }

    /** data.query：单条只读查询（SQL 或 Elasticsearch 命令），超过 maxRows 截断并标记。 */
    public Map<String, Object> query(String group, Object command, Map<String, Object> params, int maxRows) {
        var source = source(group, false);
        int limit = Math.max(1, Math.min(maxRows, maxRows()));
        return guarded(() -> {
            long start = System.currentTimeMillis();
            var call = new Call(null, deadline(), Limits.forTool(limit));
            var result = call.read(group, command, params);
            var out = new LinkedHashMap<String, Object>();
            out.put("group", group);
            out.put("type", source.kind().label());
            out.put("columns", result.columns());
            out.put("rows", result.rows());
            out.put("rowCount", result.rows().size());
            out.put("truncated", result.truncated());
            out.put("elapsedMs", System.currentTimeMillis() - start);
            return out;
        });
    }

    // ------------------------------------------------------------------ data.execute

    public record Statement(String sql, Map<String, Object> params) { }

    /** data.execute：多条语句在同一事务中按顺序执行，SELECT 返回行，写语句返回影响行数。 */
    public Map<String, Object> execute(McpCallContext context, String group, List<Statement> statements,
                                       boolean dryRun, Long expect, String requestId) {
        source(group, true);
        if (statements == null || statements.isEmpty()) throw new IllegalArgumentException("statements 不能为空");
        if (statements.size() > MAX_STATEMENTS) throw new IllegalArgumentException("一次最多 " + MAX_STATEMENTS + " 条语句");
        for (int i = 0; i < statements.size(); i++) {
            try { SqlGuard.check(statements.get(i).sql(), true); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("第 " + i + " 条语句: " + e.getMessage()); }
        }
        String fingerprint = fingerprint("execute", group, statements, expect);
        return transaction(context, group, dryRun, expect, requestId, fingerprint, Limits.forTool(maxRows()), call -> {
            var results = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < statements.size(); i++) {
                var st = statements.get(i);
                var r = new LinkedHashMap<String, Object>();
                r.put("index", i);
                try {
                    if (SqlGuard.check(st.sql(), true) == SqlGuard.Kind.WRITE) {
                        r.put("type", "write");
                        r.put("affectedRows", call.write(group, st.sql(), st.params()));
                    } else {
                        var q = call.read(group, st.sql(), st.params());
                        r.put("type", "query");
                        r.put("columns", q.columns());
                        r.put("rows", q.rows());
                        r.put("truncated", q.truncated());
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("第 " + i + " 条语句失败，整个事务已回滚: " + e.getMessage(), e);
                }
                results.add(r);
            }
            return Map.of("results", results);
        });
    }

    /**
     * 供 mcp-script 使用：writeGroup 为 null 时是纯分析，脚本只能查询；
     * 否则整个 body 在 writeGroup 的一个事务里执行，规则与 data.execute 相同。
     * body 返回的 Map 会合并到结果中（例如 result、logs）。
     */
    public Map<String, Object> script(McpCallContext context, String writeGroup, boolean dryRun, Long expect,
                                      String requestId, String scriptText, Function<McpDataScope, Map<String, Object>> body) {
        if (writeGroup == null) {
            if (expect != null || requestId != null) throw new IllegalArgumentException("没有 writeGroup 时是只读分析，不需要 expectAffectedRows 和 requestId");
            sources();
            return guarded(() -> {
                long start = System.currentTimeMillis();
                var call = new Call(null, deadline(), Limits.forScript());
                var out = new LinkedHashMap<String, Object>(body.apply(call.scope()));
                out.put("queries", call.queries);
                out.put("elapsedMs", System.currentTimeMillis() - start);
                return out;
            });
        }
        source(writeGroup, true);
        String fingerprint = fingerprint("script", writeGroup, scriptText, expect);
        return transaction(context, writeGroup, dryRun, expect, requestId, fingerprint, Limits.forScript(), call -> body.apply(call.scope()));
    }

    /** 预演或强制回滚：携带已经算好的结果穿过框架事务，让框架回滚。 */
    private static final class Rollback extends RuntimeException {
        final transient Object result;
        Rollback(Object result) { super(null, null, false, false); this.result = result; }
    }

    private Map<String, Object> transaction(McpCallContext context, String group, boolean dryRun, Long expect, String requestId,
                                            String fingerprint, Limits limits, Function<Call, Map<String, Object>> body) {
        if (!dryRun && expect == null)
            throw new IllegalArgumentException("正式执行必须传 expectAffectedRows：先用 dryRun=true 预演，再传预演得到的影响行数；确实无法预估时传 -1");
        if (expect != null && expect < -1) throw new IllegalArgumentException("expectAffectedRows 只能是 -1 或非负整数");
        if (requestId != null && !requestId.matches("[A-Za-z0-9_.:-]{1,128}"))
            throw new IllegalArgumentException("requestId 限 1–128 位字母、数字或 _.:-");
        String key = !dryRun && requestId != null ? REQUEST_KIND + "-" + digest(context.credentialId() + ":" + requestId) : null;
        if (key != null) {
            var replay = claim(key, fingerprint);
            if (replay != null) return replay;
        }
        boolean committed = false;
        try {
            var out = guarded(() -> {
                long start = System.currentTimeMillis();
                var call = new Call(group, deadline(), limits);
                Map<String, Object> result = inTransaction(call, () -> {
                    var r = new LinkedHashMap<String, Object>();
                    r.put("group", group);
                    r.put("dryRun", dryRun);
                    r.putAll(body.apply(call));
                    long total = call.affected;
                    if (!dryRun && expect != -1 && total != expect)
                        throw new IllegalArgumentException("预期影响 " + expect + " 行，实际 " + total + " 行，已全部回滚。数据可能已变化，请重新预演确认");
                    r.put("committed", !dryRun);
                    r.put("totalAffectedRows", total);
                    if (dryRun) throw new Rollback(r);
                    return r;
                });
                if (expect != null) result.put("expectAffectedRows", expect);
                result.put("queries", call.queries);
                result.put("elapsedMs", System.currentTimeMillis() - start);
                if (!dryRun && expect == -1)
                    result.put("warning", "调用方未预估影响行数（expectAffectedRows=-1），实际影响 " + result.get("totalAffectedRows") + " 行");
                if (requestId != null) result.put("requestId", requestId);
                return result;
            });
            committed = !dryRun;
            if (committed) {
                long total = ((Number) out.get("totalAffectedRows")).longValue();
                log.info("MCP 数据写入已提交 credentialId={} group={} affected={} expect={} requestId={}",
                        context.credentialId(), group, total, expect, requestId);
                if (total > 0) invalidateQueryCache();
            }
            if (key != null) finish(key, fingerprint, out);
            return out;
        } catch (RuntimeException | Error e) {
            if (key != null && !committed) release(key);
            throw e;
        }
    }

    /** 在框架事务中执行 body；Rollback 携带的结果在回滚后返回。 */
    @SuppressWarnings("unchecked")
    private <T> T inTransaction(Call call, Callable<T> body) {
        try {
            return translate(() -> route().transaction(body, call.remaining()));
        } catch (Rollback rollback) {
            return (T) rollback.result;
        }
    }

    // ------------------------------------------------------------------ 幂等

    public record Replay(String key, String fingerprint, String state, Map<String, Object> result, long createdAt) { }

    /** 返回已完成的结果（重放）；返回 null 表示已占用编号，可以执行。 */
    private Map<String, Object> claim(String key, String fingerprint) {
        for (int attempt = 0; attempt < 2; attempt++) {
            var existing = store.get(key, Replay.class);
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint))
                    throw new McpException(409, "requestId 已用于不同的语句或参数，请换一个编号");
                if (!"done".equals(existing.state()))
                    throw new McpException(409, "同一 requestId 正在执行，或上次提交后结果未能保存；请先核对数据，再换编号重试");
                var replay = new LinkedHashMap<String, Object>(existing.result());
                replay.put("replayed", true);
                return replay;
            }
            try {
                store.create(key, REQUEST_KIND, new Replay(key, fingerprint, "running", null, System.currentTimeMillis()));
                return null;
            } catch (RuntimeException duplicate) {
                // 并发提交同一编号：重新读取
            }
        }
        throw new McpException(409, "同一 requestId 正在执行");
    }

    private void finish(String key, String fingerprint, Map<String, Object> result) {
        try { store.save(key, REQUEST_KIND, new Replay(key, fingerprint, "done", result, System.currentTimeMillis())); }
        catch (RuntimeException e) { log.warn("MCP 数据写入幂等结果保存失败，同一 requestId 重试将被拒绝", e); }
    }

    private void release(String key) {
        try { store.delete(key); } catch (RuntimeException e) { log.warn("MCP 数据写入幂等编号释放失败", e); }
    }

    /** 幂等记录保留 retention-days 天，过期后同一 requestId 可以再次执行。 */
    @Override
    public void clean() {
        long cutoff = System.currentTimeMillis() - properties.getRetentionDays() * 86_400_000L;
        var expired = new ArrayList<String>();
        for (int page = 1; page <= 200; page++) {
            var rows = store.list(REQUEST_KIND, page, 200, Replay.class);
            for (var row : rows) if (row.createdAt() < cutoff && row.key() != null) expired.add(row.key());
            if (rows.size() < 200) break;
        }
        for (var key : expired) release(key);
    }

    // ------------------------------------------------------------------ 执行细节

    private static RouteSession route() {
        RouteSession route = SessionUtil.route();
        if (route == null) throw new IllegalStateException("ORM 数据源尚未初始化");
        return route;
    }

    private long deadline() { return System.currentTimeMillis() + timeoutSeconds() * 1000L; }

    private <T> T guarded(Supplier<T> action) {
        if (!slots.tryAcquire()) throw new McpException(429, "数据工具并发调用过多，请稍后再试");
        try { return action.get(); }
        finally { slots.release(); }
    }

    /**
     * 把框架和驱动的异常转成给 AI 看的 IllegalArgumentException：超时、SQL 错误、解析错误等。
     * Rollback、McpException、IllegalArgumentException 和 Error 原样抛出。
     */
    private <T> T translate(Callable<T> action) {
        try {
            return action.call();
        } catch (Rollback | McpException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof java.sql.SQLTimeoutException || t instanceof TimeoutException)
                    throw new IllegalArgumentException("执行超时（" + timeoutSeconds() + " 秒），已回滚", e);
                if (t instanceof IllegalArgumentException iae && t != e) throw new IllegalArgumentException(iae.getMessage(), e);
                if (t instanceof java.sql.SQLException sql) throw new IllegalArgumentException("执行失败: " + sql.getMessage(), e);
            }
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException("执行失败: " + message, e);
        }
    }

    /** JDBC 查询：框架原生 SQL，TypedRow 结果，驱动层最多读取 fetch 行。 */
    private List<TypedRow> jdbcRows(String group, SqlText.Bound bound, int fetch) {
        var command = new CommandNode<>(bound.sql(), bound.params(), ParseType.select).maxRows(fetch);
        QueryRequest<TypedRow> request = QueryRequest.newInstance(TypedRow.class, command).cache(false);
        request.setSession(group);
        return request.selectList();
    }

    /** 在一个带超时、最终回滚的框架事务中读取 JDBC 数据源。 */
    @SuppressWarnings("unchecked")
    private List<TypedRow> isolatedJdbcRows(String group, SqlText.Bound bound, int fetch, Duration timeout) {
        try {
            translate(() -> route().transaction(() -> { throw new Rollback(jdbcRows(group, bound, fetch)); }, timeout));
            throw new IllegalStateException("只读事务没有回滚");
        } catch (Rollback rollback) {
            return (List<TypedRow>) rollback.result;
        }
    }

    /** Elasticsearch 原生只读命令（已经过 CommandGuard 改写）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> nativeRows(String group, String command, Map<String, Object> params, long deadline) {
        // 没有事务的数据源不登记到框架事务，写事务进行中也直接执行
        return translate(() -> {
            QueryRequest<Map> request = QueryRequest.newInstance(Map.class, new CommandNode<>(command, params, ParseType.select)).cache(false);
            request.setSession(group);
            return (List) request.selectList();
        });
    }

    private void invalidateQueryCache() {
        try {
            var registry = caches.get();
            if (registry != null) registry.defaultCache().invalidateAll();
        } catch (RuntimeException e) {
            log.warn("MCP 数据写入后清理 ORM 查询缓存失败", e);
        }
    }

    record Limits(int maxRows, boolean truncate, long budget, int maxCellChars) {
        static Limits forTool(int maxRows) { return new Limits(maxRows, true, Long.MAX_VALUE, MAX_CELL_CHARS); }
        static Limits forScript() { return new Limits(SCRIPT_ROW_BUDGET, false, SCRIPT_ROW_BUDGET, SCRIPT_CELL_CHARS); }
    }

    /** 一次工具调用的执行状态：写数据源（可选）、截止时间、行数预算、影响行数统计。 */
    private final class Call {
        final String writeGroup;
        final long deadline;
        final Limits limits;
        long rowsRead, affected;
        int queries;

        Call(String writeGroup, long deadline, Limits limits) { this.writeGroup = writeGroup; this.deadline = deadline; this.limits = limits; }

        Duration remaining() {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) throw new IllegalArgumentException("执行超时（" + timeoutSeconds() + " 秒），已回滚");
            return Duration.ofMillis(left);
        }

        /** 本次查询最多返回的行数：工具调用为 maxRows，脚本为剩余预算。 */
        private int cap() {
            long left = limits.budget() - rowsRead;
            if (left <= 0) throw new IllegalArgumentException("脚本内查询合计超过 " + limits.budget() + " 行，请先过滤或聚合");
            return (int) Math.min(limits.truncate() ? limits.maxRows() : left, Integer.MAX_VALUE - 1);
        }

        QueryResult read(String group, Object command, Map<String, Object> params) {
            var source = source(group, false);
            int cap = cap();
            queries++;
            List<? extends Map<String, Object>> rows;
            if (source.kind() == DataKind.JDBC) {
                if (!(command instanceof String sql)) throw new IllegalArgumentException("JDBC 数据源的 command 是一条 SQL 字符串");
                boolean inTx = group.equals(writeGroup);
                if (SqlGuard.check(sql, inTx) == SqlGuard.Kind.WRITE)
                    throw new IllegalArgumentException("查询里不能执行写语句" + (writeGroup == null ? "" : "，写入请用 execute"));
                var bound = SqlText.bind(sql, params);
                // 写事务进行中：任何 JDBC 组都登记到同一个框架事务（多数据源事务），能看到本事务的写入；否则用只读事务读完回滚
                if (route().isOpenTransaction()) rows = translate(() -> jdbcRows(group, bound, cap + 1));
                else rows = isolatedJdbcRows(group, bound, cap + 1, remaining());
            } else {
                ObjectNode parsed = CommandGuard.parse(json, command);
                long left = remaining().toMillis();
                parsed = CommandGuard.elasticsearch(parsed, cap + 1, (int) Math.max(1, left / 1000));
                rows = nativeRows(group, parsed.toString(), params == null ? Map.of() : params, deadline);
            }
            return collect(rows, cap);
        }

        private QueryResult collect(List<? extends Map<String, Object>> raw, int cap) {
            boolean truncated = false;
            if (raw.size() > cap) {
                if (!limits.truncate())
                    throw new IllegalArgumentException("脚本内查询合计超过 " + limits.budget() + " 行，请先过滤或聚合");
                truncated = true;
                raw = raw.subList(0, cap);
            }
            var columns = new LinkedHashSet<String>();
            var rows = new ArrayList<Map<String, Object>>(raw.size());
            for (var row : raw) {
                var out = new LinkedHashMap<String, Object>();
                for (var e : row.entrySet()) {
                    columns.add(e.getKey());
                    out.put(e.getKey(), DataValues.read(e.getValue(), limits.maxCellChars()));
                }
                rows.add(out);
            }
            rowsRead += rows.size();
            return new QueryResult(List.copyOf(columns), rows, truncated);
        }

        long write(String group, String sql, Map<String, Object> params) {
            if (writeGroup == null) throw new IllegalArgumentException("只读分析不能写入；需要写入时请在调用参数里声明 writeGroup");
            if (!writeGroup.equals(group))
                throw new IllegalArgumentException("一次调用只能写入 writeGroup（" + writeGroup + "），不能写 " + group);
            if (SqlGuard.check(sql, true) != SqlGuard.Kind.WRITE) throw new IllegalArgumentException("execute 只能执行写语句，查询请用 query");
            var bound = SqlText.bind(sql, params);
            remaining();
            Integer n = translate(() -> {
                ExecuteRequest<Object> request = ExecuteRequest.newInstance(bound.sql(), bound.params());
                request.setSession(group);
                return request.execute();
            });
            long count = n == null ? 0 : n;
            affected += count;
            queries++;
            return count;
        }

        McpDataScope scope() {
            return new McpDataScope() {
                public List<Map<String, Object>> query(String group, String command, Map<String, Object> params) { return read(group, command, params).rows(); }
                public Map<String, Object> queryOne(String group, String command, Map<String, Object> params) {
                    var rows = read(group, command, params).rows();
                    if (rows.size() > 1) throw new IllegalArgumentException("queryOne 期望最多一行，实际返回多行");
                    return rows.isEmpty() ? null : rows.getFirst();
                }
                public long execute(String group, String sql, Map<String, Object> params) { return write(group, sql, params); }
            };
        }
    }

    private String fingerprint(Object... parts) {
        try { return digest(json.writeValueAsString(parts)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
