package com.yulinlin.jdbc.h2.parse;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** H2 identifier quoting; JSON values are stored as text and queried with explicit H2 SQL. */
public class NameParse extends com.yulinlin.jdbc.sql.parse.NameParse {
    private static final Pattern REFERENCE = Pattern.compile(
            "[\\p{L}_][\\p{L}\\p{N}_$]*(?:\\.(?:[\\p{L}_][\\p{L}\\p{N}_$]*|\\*))*");

    @Override public String alias(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    @Override public String selectAlias(String name) { return alias(name); }
    @Override public String reference(String name) {
        if (name.equals("*") || !REFERENCE.matcher(name).matches()) return name;
        return Arrays.stream(name.split("\\."))
                .map(part -> part.equals("*") ? part : alias(part))
                .collect(Collectors.joining("."));
    }
    @Override public String jsonExtract(String column, List<String> path, Object comparisonValue) {
        throw new UnsupportedOperationException(
                "H2 JSON-path predicates are not generated automatically; use an explicit H2 SQL expression");
    }
}
