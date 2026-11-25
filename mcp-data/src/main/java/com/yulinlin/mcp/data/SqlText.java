package com.yulinlin.mcp.data;

import java.lang.reflect.Array;
import java.util.*;

/**
 * 把调用方的 {@code #{name}} 占位符改写成框架原生 SQL 使用的 {@code #{p0}, #{p1} ...}，并给出对应参数。
 * <ul>
 *   <li>集合或数组参数展开成 {@code #{p0}, #{p1}, ...}，用于 {@code IN (#{ids})}；</li>
 *   <li>框架按正则替换占位符，不识别字符串和注释，所以字符串、注释里出现 {@code #{} 或 {@code ${} 时直接拒绝；</li>
 *   <li>缺少参数、传入未使用的参数、空数组都会报错。</li>
 * </ul>
 */
public final class SqlText {
    private SqlText() { }

    /** 单个参数展开后的元素上限。 */
    static final int MAX_LIST = 1000;

    public record Bound(String sql, Map<String, Object> params) { }

    public static Bound bind(String sql, Map<String, ?> params) {
        Map<String, ?> source = params == null ? Map.of() : params;
        if (sql.contains("${"))
            throw new IllegalArgumentException("不支持 ${...} 文本替换，值请用 #{名称} 占位并通过 params 传入");
        var out = new StringBuilder(sql.length());
        var values = new LinkedHashMap<String, Object>();
        var used = new HashSet<String>();
        int i = 0, n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            int end;
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < n) {
                    char d = sql.charAt(j);
                    if (d == '\\' && c == '\'' && j + 1 < n) { j += 2; continue; }
                    if (d == c) { if (j + 1 < n && sql.charAt(j + 1) == c) { j += 2; continue; } break; }
                    j++;
                }
                end = Math.min(j + 1, n);
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                end = sql.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
            } else if (c == '$' && i + 1 < n && sql.charAt(i + 1) == '$') {
                end = sql.indexOf("$$", i + 2);
                end = end < 0 ? n : end + 2;
            } else if (c == '#' && i + 1 < n && sql.charAt(i + 1) == '{') {
                int close = sql.indexOf('}', i + 2);
                if (close < 0) throw new IllegalArgumentException("参数占位符 #{ 没有闭合");
                String name = sql.substring(i + 2, close).strip();
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) throw new IllegalArgumentException("无效的参数名: " + name);
                if (!source.containsKey(name)) throw new IllegalArgumentException("缺少参数: " + name);
                used.add(name);
                Object value = source.get(name);
                List<Object> expanded = expand(value);
                if (expanded == null) {
                    out.append(placeholder(values, scalar(name, value)));
                } else {
                    if (expanded.isEmpty()) throw new IllegalArgumentException("参数 " + name + " 是空数组，IN () 在多数数据库中不合法");
                    if (expanded.size() > MAX_LIST) throw new IllegalArgumentException("参数 " + name + " 最多 " + MAX_LIST + " 个元素");
                    for (int k = 0; k < expanded.size(); k++) {
                        if (k > 0) out.append(", ");
                        out.append(placeholder(values, scalar(name, expanded.get(k))));
                    }
                }
                i = close + 1;
                continue;
            } else {
                out.append(c);
                i++;
                continue;
            }
            String literal = sql.substring(i, end);
            if (literal.contains("#{"))
                throw new IllegalArgumentException("字符串或注释里不能出现 #{，请把值改为参数传入");
            out.append(literal);
            i = end;
        }
        var unused = new TreeSet<String>(source.keySet());
        unused.removeAll(used);
        if (!unused.isEmpty()) throw new IllegalArgumentException("SQL 中没有用到的参数: " + unused + "，请用 #{名称} 引用");
        return new Bound(out.toString(), values);
    }

    private static String placeholder(Map<String, Object> values, Object value) {
        String key = "p" + values.size();
        values.put(key, value);
        return "#{" + key + "}";
    }

    private static Object scalar(String name, Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        throw new IllegalArgumentException("参数 " + name + " 只能是字符串、数字、布尔、null，或由它们组成的数组");
    }

    private static List<Object> expand(Object value) {
        if (value instanceof Collection<?> c) return new ArrayList<>(c);
        if (value != null && value.getClass().isArray() && !(value instanceof byte[])) {
            int len = Array.getLength(value);
            var list = new ArrayList<Object>(len);
            for (int k = 0; k < len; k++) list.add(Array.get(value, k));
            return list;
        }
        return null;
    }
}
