package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.anno.JoinAggregations;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinLazy;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinMetrics;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinTableList;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.jdbc.schema.EntityIndexResolver;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Builds entity mappings once and ensures tables using a caller-owned connection. No scans or commits. */
public final class SqliteSchemaManager {
    private record EntityType(Class<?> type, boolean underscore) { }
    private record Column(String name, String type, boolean primary) { }
    private record Table(String name, List<Column> columns, List<EntityIndexResolver.Definition> indexes) { }
    private record ActualIndex(List<String> columns, boolean unique, boolean plain) { }
    private final Map<EntityType, Optional<Table>> mappings = new ConcurrentHashMap<>();

    boolean isTableEntity(Class<?> entity, boolean underscore) {
        return table(entity, underscore) != null;
    }

    private Table table(Class<?> entity, boolean underscore) {
        if (entity == null) return null;
        return mappings.computeIfAbsent(new EntityType(entity, underscore), key -> {
            JoinTable annotation = AnnotationUtil.findAnnotation(key.type(), JoinTable.class);
            return isSimpleTable(key.type(), annotation)
                    ? Optional.of(describe(key.type(), annotation, key.underscore())) : Optional.empty();
        }).orElse(null);
    }

    /** Does not close the connection, change autoCommit, or complete the caller's transaction. */
    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore) {
        Table table = table(entity, underscore);
        if (table == null) return false;
        Objects.requireNonNull(connection, "connection");
        try {
            if (!exists(connection, table.name())) {
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalStateException("SQLite table " + table.name()
                            + " is missing; initialize it outside a read-only transaction");
                }
                StringJoiner columns = new StringJoiner(", ");
                for (Column column : table.columns()) {
                    columns.add(quote(column.name()) + " " + column.type()
                            + (column.primary() ? " NOT NULL PRIMARY KEY" : ""));
                }
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + quote(table.name()) + " (" + columns + ")");
                }
            }
            validate(connection, table);
            ensureIndexes(connection, table);
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite schema creation/validation failed for "
                    + entity.getName() + " (" + table.name() + "): " + e.getMessage(), e);
        }
    }

    private static boolean isSimpleTable(Class<?> type, JoinTable table) {
        return table != null && !table.value().isBlank() && table.left().isEmpty() && table.right().isEmpty()
                && table.on().isEmpty() && AnnotationUtil.findAnnotation(type, JoinTableList.class) == null
                && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())
                && ReflectionUtil.getAllDeclaredFields(type).stream().noneMatch(field ->
                        AnnotationUtil.findAnnotation(field, JoinAggregations.class) != null
                                || AnnotationUtil.findAnnotation(field, JoinMetrics.class) != null);
    }

    private Table describe(Class<?> entity, JoinTable table, boolean underscore) {
        identifier(table.value());
        var aliases = AliasContent.newInstance(entity, underscore);
        Map<String, Column> columns = new TreeMap<>();
        Map<String, String> persistentFields = new HashMap<>();
        for (var field : ReflectionUtil.getAllDeclaredFields(entity)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isSynthetic()) continue;
            JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
            if (mapping != null && (!mapping.exist() || !mapping.function().isEmpty())) continue;
            if (AnnotationUtil.findAnnotation(field, JoinQuery.class) != null
                    || AnnotationUtil.findAnnotation(field, JoinLazy.class) != null) continue;
            String name = aliases.toColumn(field.getName());
            // Qualified names are fields of a joined/query-only model, not physical local columns.
            if (name.contains(".")) throw new IllegalArgumentException("Qualified SQLite column in " + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            var column = new Column(name, storageType(field.getType()), meta != null && meta.primaryKey());
            if (columns.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null) {
                throw new IllegalArgumentException("Duplicate SQLite column " + table.value() + "." + name);
            }
            persistentFields.putIfAbsent(field.getName(), name);
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("No persistent columns: " + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException("Composite primary keys are not supported by ORM model operations: " + entity.getName());
        }
        return new Table(table.value(), List.copyOf(columns.values()),
                EntityIndexResolver.resolve(entity, table.value(), persistentFields));
    }

    private static String storageType(Class<?> type) {
        // ByteArrayCoder also emits Base64 text, so byte[] follows the TEXT fallback.
        if (type == boolean.class || type == Boolean.class || type == byte.class || type == Byte.class
                || type == short.class || type == Short.class || type == int.class || type == Integer.class
                || type == long.class || type == Long.class) return "INTEGER";
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) return "REAL";
        // Dates, enums, BigDecimal/BigInteger, strings and JSON values retain their existing codecs.
        return "TEXT";
    }

    private static boolean exists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT type FROM sqlite_schema WHERE name = ? COLLATE NOCASE")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return false;
                if (!"table".equals(rows.getString(1))) throw new IllegalStateException("SQLite object is not a table: " + name);
                return true;
            }
        }
    }

    private static void validate(Connection connection, Table table) throws SQLException {
        Map<String, Column> actual = new HashMap<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA table_xinfo(" + quote(table.name()) + ")")) {
            while (rows.next()) {
                String name = rows.getString("name");
                if (rows.getInt("hidden") != 0) {
                    if (table.columns().stream().anyMatch(c -> c.name().equalsIgnoreCase(name))) {
                        throw new IllegalStateException("SQLite mapped column is generated/hidden: " + table.name() + "." + name);
                    }
                    continue;
                }
                actual.put(name.toLowerCase(Locale.ROOT), new Column(name, affinity(rows.getString("type")), rows.getInt("pk") != 0));
            }
        }
        for (Column expected : table.columns()) {
            Column column = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (column == null || !column.type().equals(expected.type()) || column.primary() != expected.primary()) {
                throw new IllegalStateException("Incompatible SQLite column " + table.name() + "." + expected.name()
                        + ": expected " + expected + ", actual " + column + "; migrate manually");
            }
        }
        if (actual.values().stream().filter(Column::primary).count() != table.columns().stream().filter(Column::primary).count()) {
            throw new IllegalStateException("Incompatible SQLite primary key: " + table.name());
        }
    }

    private static void ensureIndexes(Connection connection, Table table) throws SQLException {
        if (table.indexes().isEmpty()) return;
        Map<String, ActualIndex> actual = readIndexes(connection, table.name());
        boolean created = false;
        for (EntityIndexResolver.Definition expected : table.indexes()) {
            ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalStateException("SQLite index " + expected.name()
                            + " is missing; initialize it outside a read-only transaction");
                }
                createIndex(connection, table.name(), expected);
                created = true;
            } else {
                validateIndex(table.name(), expected, found);
            }
        }
        if (created) {
            actual = readIndexes(connection, table.name());
            for (EntityIndexResolver.Definition expected : table.indexes()) {
                ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
                if (found == null) {
                    throw new IllegalStateException("SQLite index was not created: " + expected.name());
                }
                validateIndex(table.name(), expected, found);
            }
        }
    }

    private static Map<String, ActualIndex> readIndexes(Connection connection, String table) throws SQLException {
        record IndexHeader(String name, boolean unique, boolean partial) { }
        List<IndexHeader> headers = new ArrayList<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA index_list(" + quote(table) + ")")) {
            while (rows.next()) {
                headers.add(new IndexHeader(rows.getString("name"), rows.getInt("unique") != 0,
                        rows.getInt("partial") != 0));
            }
        }

        Map<String, ActualIndex> indexes = new HashMap<>();
        for (IndexHeader header : headers) {
            TreeMap<Integer, String> columns = new TreeMap<>();
            boolean plain = !header.partial();
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("PRAGMA index_xinfo(" + quote(header.name()) + ")")) {
                while (rows.next()) {
                    if (rows.getInt("key") == 0) continue;
                    String column = rows.getString("name");
                    columns.put(rows.getInt("seqno"), column == null ? "<expression>" : column);
                    if (rows.getInt("desc") != 0) plain = false;
                }
            }
            indexes.put(header.name().toLowerCase(Locale.ROOT),
                    new ActualIndex(List.copyOf(columns.values()), header.unique(), plain));
        }
        return indexes;
    }

    private static void createIndex(Connection connection, String table,
                                    EntityIndexResolver.Definition index) throws SQLException {
        String columns = index.columns().stream().map(SqliteSchemaManager::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        String sql = "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX IF NOT EXISTS "
                + quote(index.name()) + " ON " + quote(table) + " (" + columns + ")";
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void validateIndex(String table, EntityIndexResolver.Definition expected, ActualIndex actual) {
        if (expected.unique() != actual.unique() || !actual.plain()
                || !sameColumns(expected.columns(), actual.columns())) {
            throw new IllegalStateException("Incompatible SQLite index " + table + "." + expected.name()
                    + ": expected columns=" + expected.columns() + ", unique=" + expected.unique()
                    + "; actual columns=" + actual.columns() + ", unique=" + actual.unique()
                    + "; migrate manually");
        }
    }

    private static boolean sameColumns(List<String> left, List<String> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!left.get(index).equalsIgnoreCase(right.get(index))) return false;
        }
        return true;
    }

    private static String affinity(String declared) {
        String type = declared.toUpperCase(Locale.ROOT);
        if (type.contains("INT")) return "INTEGER";
        if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) return "TEXT";
        if (type.isEmpty() || type.contains("BLOB")) return "BLOB";
        if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) return "REAL";
        return "NUMERIC";
    }

    private static void identifier(String name) {
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*") || name.toLowerCase(Locale.ROOT).startsWith("sqlite_")) {
            throw new IllegalArgumentException("SQLite schema needs a plain table/column name: " + name);
        }
    }
    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}
