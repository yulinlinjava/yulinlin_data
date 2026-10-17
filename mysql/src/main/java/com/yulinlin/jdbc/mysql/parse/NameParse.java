package com.yulinlin.jdbc.mysql.parse;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Quotes MySQL identifiers while leaving explicit SQL expressions unchanged. */
public final class NameParse extends com.yulinlin.jdbc.sql.parse.NameParse {
    private static final Pattern REFERENCE = Pattern.compile(
            "[\\p{L}_][\\p{L}\\p{N}_$]*(?:\\.(?:[\\p{L}_][\\p{L}\\p{N}_$]*|\\*))*");

    @Override
    public String alias(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    @Override
    public String selectAlias(String name) {
        return alias(name);
    }

    @Override
    public String reference(String name) {
        if (name.equals("*") || !REFERENCE.matcher(name).matches()) return name;
        return Arrays.stream(name.split("\\."))
                .map(part -> part.equals("*") ? part : alias(part))
                .collect(Collectors.joining("."));
    }
}
