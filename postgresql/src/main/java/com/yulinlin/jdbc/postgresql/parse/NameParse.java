package com.yulinlin.jdbc.postgresql.parse;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** PostgreSQL identifiers and grouped aliases; JSON operations belong in explicit SQL. */
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
}
