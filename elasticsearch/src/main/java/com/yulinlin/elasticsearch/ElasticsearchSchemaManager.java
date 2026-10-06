package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinLazy;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.TextTypeEnum;
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Creates and validates Elasticsearch indexes owned by {@code @JoinTable(autoSchema = true)} entities. */
public final class ElasticsearchSchemaManager {
    private record Index(String name, Map<String, Property> properties) { }

    public List<String> createIndexDefinition(Class<?> entity, boolean underscore) {
        Index index = index(entity, underscore);
        if (index == null) return List.of();
        String fields = index.properties().entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":{\"type\":\""
                        + entry.getValue()._kind().jsonValue() + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        return List.of("PUT /" + index.name() + " {\"mappings\":{\"properties\":{" + fields + "}}}");
    }

    public void initialize(ElasticsearchClient client, Collection<Class<?>> entities,
                           boolean underscore, SchemaMode mode) {
        if (mode == SchemaMode.NONE || entities == null || entities.isEmpty()) return;
        for (Class<?> entity : entities) {
            Index expected = index(entity, underscore);
            if (expected == null) continue;
            try {
                ensureIndex(client, expected, mode);
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw new IllegalStateException("Cannot initialize Elasticsearch index "
                        + expected.name() + " from " + entity.getName(), error);
            }
        }
    }

    private static void ensureIndex(ElasticsearchClient client, Index expected, SchemaMode mode) throws Exception {
        var indices = client.indices();
        if (!indices.exists(request -> request.index(expected.name())).value()) {
            if (mode == SchemaMode.VALIDATE) {
                throw new IllegalStateException("Elasticsearch index is missing: " + expected.name());
            }
            try {
                indices.create(request -> request.index(expected.name())
                        .mappings(mapping -> mapping.properties(expected.properties())));
            } catch (Exception error) {
                // Another application instance may have created the index after the existence check.
                if (!indices.exists(request -> request.index(expected.name())).value()) throw error;
            }
        }

        var response = indices.getMapping(request -> request.index(expected.name()));
        var record = response.mappings().get(expected.name());
        if (record == null && response.mappings().size() == 1) record = response.mappings().values().iterator().next();
        if (record == null) {
            throw new IllegalStateException("Cannot read Elasticsearch mapping: " + expected.name());
        }
        Map<String, Property> actual = record.mappings().properties();
        Map<String, Property> missing = new LinkedHashMap<>();
        for (var entry : expected.properties().entrySet()) {
            Property found = actual.get(entry.getKey());
            if (found == null) {
                if (mode == SchemaMode.VALIDATE) {
                    throw new IllegalStateException("Elasticsearch field is missing: "
                            + expected.name() + "." + entry.getKey());
                }
                missing.put(entry.getKey(), entry.getValue());
            } else if (found._kind() != entry.getValue()._kind()) {
                throw new IllegalStateException("Incompatible Elasticsearch field " + expected.name() + "."
                        + entry.getKey() + ": expected " + entry.getValue()._kind().jsonValue()
                        + ", actual " + found._kind().jsonValue() + "; migrate manually");
            }
        }
        if (!missing.isEmpty()) {
            indices.putMapping(request -> request.index(expected.name()).properties(missing));
        }
    }

    private static Index index(Class<?> entity, boolean underscore) {
        if (entity == null) return null;
        JoinTable table = AnnotationUtil.findAnnotation(entity, JoinTable.class);
        if (table == null || !table.autoSchema() || table.value().isBlank()
                || !table.left().isEmpty() || !table.right().isEmpty() || !table.on().isEmpty()) return null;
        String name = table.value();
        if (!name.equals(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Elasticsearch index name must be lowercase: " + name);
        }
        AliasContent aliases = AliasContent.newInstance(entity, underscore);
        Map<String, Property> properties = new TreeMap<>();
        for (var field : ReflectionUtil.getAllDeclaredFields(entity)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                    || field.isSynthetic()) continue;
            JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
            if (mapping != null && (!mapping.exist() || !mapping.function().isEmpty())) continue;
            if (AnnotationUtil.findAnnotation(field, JoinQuery.class) != null
                    || AnnotationUtil.findAnnotation(field, JoinLazy.class) != null) continue;
            Property property = property(field.getType(), mapping);
            if (property == null) continue; // Complex JSON remains dynamically mapped by Elasticsearch.
            String fieldName = aliases.toColumn(field.getName());
            if (fieldName.contains(".")) {
                throw new IllegalArgumentException("Qualified Elasticsearch field in "
                        + entity.getName() + ": " + fieldName);
            }
            Property existing = properties.putIfAbsent(fieldName, property);
            if (existing != null) {
                throw new IllegalArgumentException("Duplicate Elasticsearch field " + name + "." + fieldName);
            }
        }
        return new Index(name, Map.copyOf(properties));
    }

    private static Property property(Class<?> type, JoinField mapping) {
        if (type == boolean.class || type == Boolean.class) return Property.of(p -> p.boolean_(v -> v));
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class) return Property.of(p -> p.integer(v -> v));
        if (type == long.class || type == Long.class || type == BigInteger.class) {
            return Property.of(p -> p.long_(v -> v));
        }
        if (type == float.class || type == Float.class || type == double.class || type == Double.class
                || type == BigDecimal.class) return Property.of(p -> p.double_(v -> v));
        if (type == byte[].class) return Property.of(p -> p.binary(v -> v));
        if (Date.class.isAssignableFrom(type) || type.getName().startsWith("java.time.")) {
            // Existing codecs emit sortable text, so keyword avoids imposing a different date format.
            return Property.of(p -> p.keyword(v -> v));
        }
        if (type == String.class || type == char.class || type == Character.class || type.isEnum()) {
            boolean fullText = mapping != null
                    && (mapping.fullText() || mapping.textType() == TextTypeEnum.text);
            return fullText ? Property.of(p -> p.text(v -> v)) : Property.of(p -> p.keyword(v -> v));
        }
        return null;
    }
}
