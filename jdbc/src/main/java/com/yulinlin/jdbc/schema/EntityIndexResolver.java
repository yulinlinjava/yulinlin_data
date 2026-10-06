package com.yulinlin.jdbc.schema;

import com.yulinlin.data.core.anno.JoinIndex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves database-neutral index declarations after entity fields have been mapped to columns. */
public final class EntityIndexResolver {
    private static final int MAX_GENERATED_NAME = 63;
    private static final int HASH_LENGTH = 8;

    public record Definition(String name, List<String> columns, boolean unique) {
        public Definition {
            columns = List.copyOf(columns);
        }
    }

    private EntityIndexResolver() { }

    public static List<Definition> resolve(Class<?> entity, String table, Map<String, String> persistentFields) {
        JoinIndex[] declarations = entity.getDeclaredAnnotationsByType(JoinIndex.class);
        if (declarations.length == 0) return List.of();

        Map<List<String>, Definition> byColumns = new LinkedHashMap<>();
        for (JoinIndex declaration : declarations) {
            if (declaration.fields().length == 0) {
                throw new IllegalArgumentException("Index fields cannot be empty: " + entity.getName());
            }
            List<String> columns = new ArrayList<>(declaration.fields().length);
            Map<String, Boolean> seenProperties = new HashMap<>();
            for (String property : declaration.fields()) {
                if (property == null || property.isBlank()) {
                    throw new IllegalArgumentException("Index property cannot be blank: " + entity.getName());
                }
                if (seenProperties.put(property, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException("Duplicate index property " + entity.getName() + "." + property);
                }
                String column = persistentFields.get(property);
                if (column == null) {
                    throw new IllegalArgumentException("Index property is missing or not persistent: "
                            + entity.getName() + "." + property);
                }
                columns.add(column);
            }

            List<String> signature = columns.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
            Definition existing = byColumns.get(signature);
            Definition definition = new Definition(generatedName(table, columns, declaration.unique()),
                    columns, declaration.unique());
            if (existing == null) {
                byColumns.put(signature, definition);
            } else if (existing.unique() == definition.unique()) {
                throw new IllegalArgumentException("Duplicate index fields on " + entity.getName() + ": " + columns);
            } else if (definition.unique()) {
                // A unique index already serves ordinary lookups for the same ordered columns.
                byColumns.put(signature, definition);
            }
        }

        Map<String, Definition> names = new HashMap<>();
        for (Definition definition : byColumns.values()) {
            Definition existing = names.putIfAbsent(definition.name().toLowerCase(Locale.ROOT), definition);
            if (existing != null && !existing.equals(definition)) {
                throw new IllegalArgumentException("Generated index name collision on " + entity.getName()
                        + ": " + definition.name());
            }
        }
        return List.copyOf(byColumns.values());
    }

    private static String generatedName(String table, List<String> columns, boolean unique) {
        String raw = ((unique ? "uk_" : "idx_") + table + "_" + String.join("_", columns))
                .toLowerCase(Locale.ROOT);
        if (raw.length() <= MAX_GENERATED_NAME) return raw;
        String suffix = "_" + sha256(raw).substring(0, HASH_LENGTH);
        return raw.substring(0, MAX_GENERATED_NAME - suffix.length()) + suffix;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }
}
