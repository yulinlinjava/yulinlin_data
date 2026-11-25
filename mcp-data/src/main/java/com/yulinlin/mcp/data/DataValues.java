package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Reader;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/** 查询结果、调用参数与 JSON 友好值之间的转换。 */
public final class DataValues {
    private DataValues() { }

    private static final long SAFE = 9_007_199_254_740_991L; // 2^53 - 1，JS 能精确表示的整数上限
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 嵌套结构（Elasticsearch 文档）的最大深度和单个数组/对象的最大元素数。 */
    private static final int MAX_DEPTH = 32;
    private static final int MAX_ITEMS = 1000;

    /**
     * 读出的值转成 JSON 友好的类型：数字、字符串、布尔、null，Map 和 List 递归转换。
     * 超长文本截断到 maxChars；超出 JS 安全精度的整数和高精度小数转为字符串。
     */
    public static Object read(Object value, int maxChars) {
        return read(value, maxChars, 0);
    }

    private static Object read(Object value, int maxChars, int depth) {
        if (value == null) return null;
        if (value instanceof Boolean || value instanceof Integer || value instanceof Short || value instanceof Byte) return value;
        if (value instanceof Long l) return Math.abs(l) <= SAFE ? l : l.toString();
        if (value instanceof BigInteger b) return b.bitLength() < 53 ? b.longValue() : b.toString();
        if (value instanceof BigDecimal d) {
            BigDecimal s = d.stripTrailingZeros();
            if (s.scale() <= 0 && s.precision() - s.scale() <= 15) return s.longValueExact();
            return s.precision() <= 15 ? s.doubleValue() : s.toPlainString();
        }
        if (value instanceof Double d) return d.isNaN() || d.isInfinite() ? d.toString() : d;
        if (value instanceof Float f) return f.isNaN() || f.isInfinite() ? f.toString() : (double) f;
        if (value instanceof String s) return truncate(s, maxChars);
        if (value instanceof Character c) return c.toString();
        if (value instanceof Timestamp t) return format(t.toLocalDateTime());
        if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (value instanceof Time t) return t.toLocalTime().toString();
        if (value instanceof LocalDateTime t) return format(t);
        if (value instanceof OffsetDateTime t) return t.toString();
        if (value instanceof ZonedDateTime t) return t.toOffsetDateTime().toString();
        if (value instanceof Instant t) return t.toString();
        if (value instanceof TemporalAccessor t) return t.toString();
        if (value instanceof java.util.Date d) return format(LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
        if (value instanceof UUID u) return u.toString();
        if (value instanceof byte[] b) return "[二进制 " + b.length + " 字节]";
        if (depth >= MAX_DEPTH) return "[嵌套过深，已省略]";
        if (value instanceof Map<?, ?> map) {
            var out = new LinkedHashMap<String, Object>();
            int count = 0;
            for (var e : map.entrySet()) {
                if (count++ >= MAX_ITEMS) { out.put("…", "[超过 " + MAX_ITEMS + " 个字段，已截断]"); break; }
                out.put(String.valueOf(e.getKey()), read(e.getValue(), maxChars, depth + 1));
            }
            return out;
        }
        if (value instanceof Collection<?> list) {
            var out = new ArrayList<Object>(Math.min(list.size(), MAX_ITEMS + 1));
            for (Object item : list) {
                if (out.size() >= MAX_ITEMS) { out.add("[超过 " + MAX_ITEMS + " 个元素，已截断]"); break; }
                out.add(read(item, maxChars, depth + 1));
            }
            return out;
        }
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            var out = new ArrayList<Object>(Math.min(len, MAX_ITEMS));
            for (int i = 0; i < Math.min(len, MAX_ITEMS); i++) out.add(read(java.lang.reflect.Array.get(value, i), maxChars, depth + 1));
            return out;
        }
        try {
            if (value instanceof Blob b) return "[二进制 " + b.length() + " 字节]";
            if (value instanceof Clob c) {
                try (Reader reader = c.getCharacterStream()) {
                    char[] buffer = new char[(int) Math.min(c.length(), maxChars + 1L)];
                    int read = reader.read(buffer);
                    return truncate(new String(buffer, 0, Math.max(read, 0)), maxChars);
                }
            }
            if (value instanceof java.sql.Array a) return read(a.getArray(), maxChars, depth);
        } catch (SQLException | java.io.IOException e) {
            return "[读取失败: " + e.getMessage() + "]";
        }
        return truncate(value.toString(), maxChars);
    }

    private static String format(LocalDateTime t) {
        String base = DATE_TIME.format(t);
        int nanos = t.getNano();
        if (nanos == 0) return base;
        return base + "." + String.format("%09d", nanos).replaceFirst("0+$", "");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…[已截断，原长 " + s.length() + "]";
    }

    /**
     * 调用方传入的 JSON 参数转成 Java 值：字符串、Long/BigDecimal、布尔、null、List、Map。
     * SQL 参数是否合法（只能是标量或标量数组）由 {@link SqlText} 检查；Elasticsearch 命令可以使用对象参数。
     */
    public static Map<String, Object> params(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return Map.of();
        if (!node.isObject()) throw new IllegalArgumentException("params 必须是对象");
        var result = new LinkedHashMap<String, Object>();
        node.fields().forEachRemaining(e -> result.put(e.getKey(), value(e.getValue(), 0)));
        return result;
    }

    private static Object value(JsonNode v, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("params 嵌套过深");
        if (v.isNull()) return null;
        if (v.isTextual()) return v.asText();
        if (v.isBoolean()) return v.asBoolean();
        if (v.isIntegralNumber()) return v.canConvertToLong() ? (Object) v.asLong() : v.decimalValue();
        if (v.isNumber()) return v.decimalValue();
        if (v.isArray()) {
            var list = new ArrayList<Object>();
            for (JsonNode item : v) list.add(value(item, depth + 1));
            return list;
        }
        if (v.isObject()) {
            var map = new LinkedHashMap<String, Object>();
            v.fields().forEachRemaining(e -> map.put(e.getKey(), value(e.getValue(), depth + 1)));
            return map;
        }
        throw new IllegalArgumentException("不支持的参数值: " + v);
    }
}
