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
                throw new IllegalArgumentException("索引字段列表为空；请为 @JoinIndex.fields 指定持久化属性。实体：" + entity.getName());
            }
            List<String> columns = new ArrayList<>(declaration.fields().length);
            Map<String, Boolean> seenProperties = new HashMap<>();
            for (String property : declaration.fields()) {
                if (property == null || property.isBlank()) {
                    throw new IllegalArgumentException("索引属性名为空；请修正 @JoinIndex.fields。实体：" + entity.getName());
                }
                if (seenProperties.put(property, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException("同一索引重复引用属性；请移除重复的 @JoinIndex.fields 项。属性：" + entity.getName() + "." + property);
                }
                String column = persistentFields.get(property);
                if (column == null) {
                    throw new IllegalArgumentException("索引引用的属性不存在或不持久化；请使用 Java 持久化属性名。属性："
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
                throw new IllegalArgumentException("索引字段声明重复；请合并重复的 @JoinIndex。实体：" + entity.getName() + ": " + columns);
            } else if (definition.unique()) {
                // A unique index already serves ordinary lookups for the same ordered columns.
                byColumns.put(signature, definition);
            }
        }

        Map<String, Definition> names = new HashMap<>();
        for (Definition definition : byColumns.values()) {
            Definition existing = names.putIfAbsent(definition.name().toLowerCase(Locale.ROOT), definition);
            if (existing != null && !existing.equals(definition)) {
                throw new IllegalArgumentException("自动生成的索引名冲突；请调整表名或索引字段声明并检查生成结果。实体：" + entity.getName()
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
            throw new IllegalStateException("运行环境缺少 SHA-256 算法，无法生成稳定索引名；请检查 JDK 安装及安全提供者", error);
        }
    }
}
