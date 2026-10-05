package com.yulinlin.jdbc.postgresql.parse;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** PostgreSQL identifiers, grouped aliases and JSON path expressions. */
public class NameParse extends com.yulinlin.jdbc.sql.parse.NameParse {
    private static final Pattern REFERENCE = Pattern.compile(
            "[\\p{L}_][\\p{L}\\p{N}_$]*(?:\\.(?:[\\p{L}_][\\p{L}\\p{N}_$]*|\\*))*");

    @Override public String alias(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    @Override public String selectAlias(String name) { return alias(name); }
    @Override public String groupReference(String name, String expression) {
        return expression == null ? alias(name) : expression;
    }
    @Override public String reference(String name) {
        if (name.equals("*") || !REFERENCE.matcher(name).matches()) return name;
        return Arrays.stream(name.split("\\."))
                .map(part -> part.equals("*") ? part : alias(part)).collect(Collectors.joining("."));
    }
    @Override public boolean supportsHavingAlias() { return false; }
    @Override public String writeReference(String column) {
        if (column.contains(com.yulinlin.jdbc.sql.parse.SqlJsonUtil.symbol)) {
            throw new UnsupportedOperationException("PostgreSQL JSON path updates require an explicit SQL expression");
        }
        return super.writeReference(column);
    }
    @Override public String jsonExtract(String column, List<String> path, Object comparisonValue) {
        String keys = path.stream().map(key -> "'" + key.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        String expression = "(CAST(" + column + " AS jsonb) #>> ARRAY[" + keys + "])";
        Object scalar = comparisonValue;
        if (scalar instanceof Collection<?> values) scalar = values.stream().filter(Objects::nonNull).findFirst().orElse(null);
        else if (scalar != null && scalar.getClass().isArray()) scalar = Array.getLength(scalar) == 0 ? null : Array.get(scalar, 0);
        if (scalar instanceof Number) return "CAST(" + expression + " AS numeric)";
        if (scalar instanceof Boolean) return "CAST(" + expression + " AS boolean)";
        return expression;
    }
}
