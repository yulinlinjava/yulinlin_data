package com.yulinlin.monitor;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinTableList;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.jdbc.session.SqlNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

final class DatabaseOperationFactory {
    private static final int MAX_SQL_LENGTH = 4096;
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final int MAX_BATCH_TEMPLATES = 5;

    private final AtomicLong ids = new AtomicLong();

    DatabaseOperation single(long elapsedMs, ParseResult result, int affectedRows,
                             boolean success, Throwable error, EntitySession session) {
        if (result == null || !(result.getRequest() instanceof SqlNode node)) return null;
        Class<?> source = sourceClass(result);
        String sql = normalizeSql(node.getSql());
        return operation(elapsedMs, result, affectedRows, success, error, session,
                entityName(source), sourceName(source), resources(source), sql);
    }

    DatabaseOperation batch(long elapsedMs, List<ParseResult> results, int affectedRows,
                            boolean success, Throwable error, EntitySession session) {
        if (results == null || results.isEmpty()) return null;
        ParseResult first = results.stream().filter(result -> result != null
                && result.getRequest() instanceof SqlNode).findFirst().orElse(null);
        if (first == null) return null;

        LinkedHashSet<String> templates = new LinkedHashSet<>();
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        for (ParseResult result : results) {
            if (result == null || !(result.getRequest() instanceof SqlNode node)) continue;
            String sql = normalizeSql(node.getSql());
            templates.add(sql);
            Class<?> source = sourceClass(result);
            String entity = entityName(source);
            if (!entity.isBlank()) entities.add(entity);
            String sourceName = sourceName(source);
            if (!sourceName.equals("-")) sources.add(sourceName);
            tables.addAll(resourceSet(source));
        }

        return operation(elapsedMs, first, affectedRows, success, error, session,
                entities.isEmpty() ? "自定义 SQL" : String.join(", ", entities),
                sources.isEmpty() ? "-" : String.join(", ", sources),
                tables.isEmpty() ? "-" : String.join(", ", tables), renderTemplates(templates));
    }

    private DatabaseOperation operation(long elapsedMs, ParseResult result, int affectedRows,
                                        boolean success, Throwable error, EntitySession session,
                                        String entity, String source, String table, String sql) {
        return new DatabaseOperation(ids.incrementAndGet(), System.currentTimeMillis(),
                session == null ? "unknown" : text(session.group(), "unknown"),
                session == null ? "unknown" : session.getClass().getSimpleName(),
                result.getType() == null ? "unknown" : result.getType().name(),
                text(entity, "自定义 SQL"), text(source, "-"), text(table, "-"),
                text(sql, "<unknown>"), elapsedMs,
                affectedRows, success, errorText(error));
    }

    private static Class<?> sourceClass(ParseResult result) {
        try {
            return result.getContext() == null ? Object.class : result.getContext().getSourceClass();
        } catch (RuntimeException ignored) {
            return Object.class;
        }
    }

    private static String entityName(Class<?> source) {
        return source == null || source == Object.class ? "自定义 SQL" : source.getSimpleName();
    }

    private static String sourceName(Class<?> source) {
        return source == null || source == Object.class ? "-" : source.getName();
    }

    private static String resources(Class<?> source) {
        Set<String> resources = resourceSet(source);
        return resources.isEmpty() ? "-" : String.join(", ", resources);
    }

    private static Set<String> resourceSet(Class<?> source) {
        LinkedHashSet<String> resources = new LinkedHashSet<>();
        if (source != null && source != Object.class) {
            JoinTableList list = AnnotationUtil.findAnnotation(source, JoinTableList.class);
            if (list != null) for (JoinTable table : list.value()) addTable(resources, table);
            JoinTable table = AnnotationUtil.findAnnotation(source, JoinTable.class);
            if (table != null) addTable(resources, table);
        }
        return resources;
    }

    private static void addTable(Set<String> resources, JoinTable table) {
        if (!table.value().isBlank()) resources.add(table.value());
        if (!table.left().isBlank()) resources.add(table.left());
        if (!table.right().isBlank()) resources.add(table.right());
    }

    private static String renderTemplates(Set<String> templates) {
        StringJoiner result = new StringJoiner("\n");
        int index = 0;
        for (String template : templates) {
            if (index++ == MAX_BATCH_TEMPLATES) break;
            result.add(template);
        }
        if (templates.size() > MAX_BATCH_TEMPLATES) {
            result.add("... (+" + (templates.size() - MAX_BATCH_TEMPLATES) + " templates)");
        }
        return truncate(result.toString(), MAX_SQL_LENGTH);
    }

    private static String normalizeSql(String sql) {
        if (sql == null) return "";
        return truncate(sql.replaceAll("\\s+", " ").trim(), MAX_SQL_LENGTH);
    }

    private static String errorText(Throwable error) {
        if (error == null) return "";
        String message = error.getMessage();
        return truncate(error.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message.replaceAll("\\s+", " ")),
                MAX_ERROR_LENGTH);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }
}
