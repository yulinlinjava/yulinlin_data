package com.yulinlin.jdbc.postgresql.parse;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.jdbc.sql.SqlParamsContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request-local query expressions used by ts_headline after WHERE has been parsed. */
public final class PostgresqlFullTextQueries {
    private static final String KEY = PostgresqlFullTextQueries.class.getName();

    private PostgresqlFullTextQueries() { }

    public static void add(IParamsContext params, String column, String query) {
        if (!(params instanceof SqlParamsContext sql)) return;
        Map<String, List<String>> queries = sql.computeAttribute(KEY, LinkedHashMap::new);
        queries.computeIfAbsent(column, ignored -> new ArrayList<>()).add(query);
    }

    @SuppressWarnings("unchecked")
    public static List<String> get(IParamsContext params, String column) {
        if (!(params instanceof SqlParamsContext sql)) return List.of();
        Object value = sql.attribute(KEY);
        if (!(value instanceof Map<?, ?> map)) return List.of();
        Object queries = map.get(column);
        return queries instanceof List<?> list ? (List<String>) list : List.of();
    }
}
