package com.yulinlin.jdbc.h2.parse;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.jdbc.sql.SqlParamsContext;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** H2 identifier quoting; JSON values are stored as text and queried with explicit H2 SQL. */
public class NameParse extends com.yulinlin.jdbc.sql.parse.NameParse {
    private static final Pattern REFERENCE = Pattern.compile(
            "[\\p{L}_][\\p{L}\\p{N}_$]*(?:\\.(?:[\\p{L}_][\\p{L}\\p{N}_$]*|\\*))*");

    @Override public String alias(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    @Override public String selectAlias(String name) { return alias(name); }
    @Override public String groupReference(String name, String expression) {
        // A selected alias may also be an input column; group by the actual expression.
        return expression == null ? alias(name) : expression;
    }
    @Override public boolean supportsHavingAlias() { return false; }
    @Override protected String resolve(String name, IParamsContext params) {
        if (!AliasUtil.ok() && params instanceof SqlParamsContext sql) {
            String expression = sql.groupExpression(name);
            if (expression != null) {
                // H2 2.x can reject computed grouping keys in HAVING, even after alias expansion.
                // The grouping expression is constant within each group, so MIN preserves its
                // value (including NULL) while satisfying H2's aggregate expression validation.
                return "MIN(" + expression + ")";
            }
        }
        // Aggregate metrics expand to their original expression, never MIN(SUM(...)).
        return super.resolve(name, params);
    }
    @Override public String reference(String name) {
        if (name.equals("*") || !REFERENCE.matcher(name).matches()) return name;
        return Arrays.stream(name.split("\\."))
                .map(part -> part.equals("*") ? part : alias(part))
                .collect(Collectors.joining("."));
    }
}
