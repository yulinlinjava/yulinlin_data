package com.yulinlin.data.core.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原生查询的“类型化行”结果类型：{@code QueryRequest.newInstance(sql, params, TypedRow.class)}。
 * <p>
 * 与 {@code Map.class} 的区别：JDBC 数据源按 {@code ResultSet.getObject} 读取原始类型（数字、日期、布尔等），
 * 值为 NULL 的列也会保留（键存在、值为 null），并保持 SELECT 中的列顺序。{@code Map.class} 的行为不变。
 * Elasticsearch 原生命令本来就返回原始类型，结果相同。
 */
public class TypedRow extends LinkedHashMap<String, Object> {
    public TypedRow() { }

    public TypedRow(Map<String, ?> values) { super(values); }
}
