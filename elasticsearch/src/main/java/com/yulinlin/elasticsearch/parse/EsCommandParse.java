package com.yulinlin.elasticsearch.parse;

import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yulinlin.data.core.exception.ParseException;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.*;
import java.io.StringReader;
import java.util.*;

/**
 * Elasticsearch 原生查询命令：{@code QueryRequest.newInstance(Map.class, CommandNode.of(json, params, ParseType.select))}。
 * <p>
 * 命令是一个 JSON 对象，用一个键说明操作和索引，其余内容就是 ES 的请求体：
 * <pre>
 * {"search": "orders-*", "query": {...}, "size": 20, "sort": [...], "_source": [...]}   → 命中文档，每条一行
 * {"aggregate": "orders-*", "size": 0, "query": {...}, "aggs": {...}}                  → 一行：{total, aggregations}
 * {"count": "orders-*", "query": {...}}                                                → 一行：{count}
 * {"mapping": "orders-*"}                                                              → 每个索引一行：{index, mappings}
 * </pre>
 * mapping 只读取字段映射，允许用 "*" 列出全部索引。
 * 参数：值恰好为 {@code "#{name}"} 的字符串节点会被替换成参数值（按 JSON 节点替换，不做文本拼接，不存在注入）。
 * 表达式可以是 JSON 字符串、Map 或 Jackson JsonNode。只支持查询，写操作请使用 Wrapper。
 */
public class EsCommandParse implements IParse<CommandNode<Object>> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, EsCommand.Kind> KINDS = Map.of(
            "search", EsCommand.Kind.SEARCH, "aggregate", EsCommand.Kind.AGGREGATE, "count", EsCommand.Kind.COUNT,
            "mapping", EsCommand.Kind.MAPPING);

    @Override
    public Object parse(CommandNode<Object> node, IParamsContext params, IParseManager parseManager) {
        if (node.getType() != ParseType.select && node.getType() != ParseType.group)
            throw new UnsupportedOperationException("Elasticsearch 原生命令只支持查询（search / aggregate / count），写入请使用 Wrapper");
        ObjectNode command = toObject(node.getExpression());
        substitute(command, node.getParams() == null ? Map.of() : node.getParams(), new HashSet<>());
        var keys = new ArrayList<String>();
        command.fieldNames().forEachRemaining(name -> { if (KINDS.containsKey(name)) keys.add(name); });
        if (keys.size() != 1)
            throw new ParseException("Elasticsearch 原生命令必须且只能包含 search、aggregate、count、mapping 中的一个键，值为索引名");
        String key = keys.getFirst();
        JsonNode indexNode = command.remove(key);
        if (indexNode == null || !indexNode.isTextual() || indexNode.asText().isBlank())
            throw new ParseException("Elasticsearch 原生命令必须指定索引，不能隐式查询全部索引");
        String index = indexNode.asText().strip();
        List<String> indices = Arrays.stream(index.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (indices.isEmpty()) throw new ParseException("Elasticsearch 原生命令必须指定索引");
        var kind = KINDS.get(key);
        if (kind == EsCommand.Kind.MAPPING) {
            if (!command.isEmpty()) throw new ParseException("mapping 命令只能包含 mapping 一个键");
            return new ParseResult(node.getType(), new EsCommand(kind, index, indices, null, null), params);
        }
        for (String name : indices)
            if (name.equals("*") || name.equals("_all") || name.startsWith("-"))
                throw new ParseException("Elasticsearch 原生命令不能查询全部索引（*、_all）或使用排除写法，请写明索引或带前缀的通配符");
        String body = command.toString();
        EsCommand result;
        try {
            if (kind == EsCommand.Kind.COUNT) {
                CountRequest count = new CountRequest.Builder().withJson(new StringReader(body)).index(indices).build();
                result = new EsCommand(kind, index, indices, null, count);
            } else {
                SearchRequest search = new SearchRequest.Builder().withJson(new StringReader(body)).index(indices).build();
                result = new EsCommand(kind, index, indices, search, null);
            }
        } catch (RuntimeException invalid) {
            throw new ParseException("Elasticsearch 原生命令的请求体无效：" + invalid.getMessage());
        }
        return new ParseResult(node.getType(), result, params);
    }

    private static ObjectNode toObject(Object expression) {
        try {
            JsonNode tree;
            if (expression instanceof String text) tree = JSON.readTree(text);
            else if (expression instanceof JsonNode json) tree = json.deepCopy();
            else if (expression instanceof Map<?, ?> map) tree = JSON.valueToTree(map);
            else throw new ParseException("Elasticsearch 原生命令必须是 JSON 字符串、Map 或 JsonNode："
                        + (expression == null ? "null" : expression.getClass().getName()));
            if (!(tree instanceof ObjectNode object)) throw new ParseException("Elasticsearch 原生命令必须是 JSON 对象");
            return object;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ParseException("Elasticsearch 原生命令不是合法的 JSON：" + e.getOriginalMessage());
        }
    }

    /** 把值恰好为 "#{name}" 的字符串节点替换成参数值；未使用的参数报错，避免参数名写错被静默忽略。 */
    static void substitute(ObjectNode root, Map<String, ?> params, Set<String> used) {
        replace(root, params, used);
        var unused = new TreeSet<String>(params.keySet());
        unused.removeAll(used);
        if (!unused.isEmpty()) throw new ParseException("命令中没有用到的参数：" + unused + "，请用 \"#{名称}\" 引用");
    }

    private static void replace(JsonNode node, Map<String, ?> params, Set<String> used) {
        if (node instanceof ObjectNode object) {
            var names = new ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                String param = placeholder(child);
                if (param != null) object.set(name, value(param, params, used));
                else replace(child, params, used);
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                String param = placeholder(array.get(i));
                if (param != null) array.set(i, value(param, params, used));
                else replace(array.get(i), params, used);
            }
        }
    }

    private static String placeholder(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String text = node.asText();
        if (!text.startsWith("#{") || !text.endsWith("}")) return null;
        String name = text.substring(2, text.length() - 1).strip();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) throw new ParseException("无效的参数名：" + name);
        return name;
    }

    private static JsonNode value(String name, Map<String, ?> params, Set<String> used) {
        if (!params.containsKey(name)) throw new ParseException("缺少参数：" + name);
        used.add(name);
        return JSON.valueToTree(params.get(name));
    }
}
