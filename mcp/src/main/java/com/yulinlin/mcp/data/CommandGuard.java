package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/**
 * Elasticsearch 只读命令的检查和改写：限制返回条数、注入服务端超时。
 * 命令格式见 doc/datasources/elasticsearch.md「原生查询命令」；
 * 参数占位（值恰好是 "#{名称}" 的字符串）由框架在 JSON 节点上替换。
 */
public final class CommandGuard {
    private CommandGuard() { }

    static final int MAX_COMMAND_CHARS = 100_000;
    private static final Set<String> ES_OPS = Set.of("search", "aggregate", "count", "mapping");

    /** 把字符串或 JSON 对象形式的命令解析成对象节点。 */
    public static ObjectNode parse(ObjectMapper json, Object command) {
        JsonNode node;
        if (command instanceof JsonNode n) node = n.isTextual() ? read(json, n.asText()) : n.deepCopy();
        else if (command instanceof String s) node = read(json, s);
        else if (command instanceof Map<?, ?> m) node = json.valueToTree(m);
        else throw new IllegalArgumentException("command 必须是 JSON 对象或 JSON 字符串");
        if (!(node instanceof ObjectNode object)) throw new IllegalArgumentException("command 必须是 JSON 对象");
        return object;
    }

    private static JsonNode read(ObjectMapper json, String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("command 不能为空");
        if (text.length() > MAX_COMMAND_CHARS) throw new IllegalArgumentException("command 最长 " + MAX_COMMAND_CHARS + " 字符");
        try { return json.readTree(text); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("command 不是合法的 JSON: " + e.getOriginalMessage()); }
    }

    /**
     * Elasticsearch：search 的 size 不超过 cap（未写时取 cap），aggregate 未写 size 时为 0；
     * search、aggregate 注入 timeout。写入类操作不在 ES 命令支持范围内，由框架拒绝。
     */
    public static ObjectNode elasticsearch(ObjectNode command, int cap, int timeoutSeconds) {
        var ops = new ArrayList<String>();
        command.fieldNames().forEachRemaining(k -> { if (ES_OPS.contains(k)) ops.add(k); });
        if (ops.size() != 1)
            throw new IllegalArgumentException("Elasticsearch 命令必须且只能有一个操作键：search、aggregate、count、mapping，值是索引名");
        String op = ops.getFirst();
        if (op.equals("search")) {
            command.put("size", Math.min(intField(command, "size", cap), cap));
            command.put("timeout", timeoutSeconds + "s");
        } else if (op.equals("aggregate")) {
            if (!command.has("size")) command.put("size", 0);
            else command.put("size", Math.min(intField(command, "size", 0), cap));
            command.put("timeout", timeoutSeconds + "s");
        }
        return command;
    }

    private static int intField(ObjectNode command, String name, int absent) {
        JsonNode v = command.get(name);
        if (v == null || v.isNull()) return absent;
        if (!v.canConvertToInt() || !v.isIntegralNumber()) throw new IllegalArgumentException(name + " 必须直接写整数，不能用参数占位");
        if (v.asInt() < 0) throw new IllegalArgumentException(name + " 不能为负数");
        return v.asInt();
    }

}
