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
import com.yulinlin.data.core.anno.TextTypeEnum;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.jdbc.schema.EntityIndexResolver;
import com.yulinlin.jdbc.schema.SchemaSqlExecutor;
import com.yulinlin.jdbc.schema.TextColumnResolver;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Builds entity mappings once and ensures tables using a caller-owned connection. No scans or commits. */
public final class SqliteSchemaManager {
    private record EntityType(Class<?> type, boolean underscore) { }
    private record Column(String name, String type, String ddl, boolean primary,
                          boolean largeText, int textLength) { }
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
    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            SchemaSqlExecutor executor) {
        Table table = table(entity, underscore);
        if (table == null) return false;
        Objects.requireNonNull(connection, "connection");
        try {
            if (!exists(connection, table.name())) {
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalStateException("SQLite table " + table.name()
                            + " is missing; initialize it outside a read-only transaction");
                }
                executor.execute(createBaseTableSql(table));
            }
            Map<String, Column> actual = readColumns(connection, table);
            actual = ensureColumns(connection, table, actual, executor);
            validate(table, actual);
            ensureIndexes(connection, table, executor);
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite schema creation/validation failed for "
                    + entity.getName() + " (" + table.name() + "): " + e.getMessage(), e);
        }
    }

    /** Returns the exact initial DDL used by SqliteSession without touching the database. */
    public List<String> createTableSql(Class<?> entity, boolean underscore) {
        Table table = table(entity, underscore);
        return table == null ? List.of() : createTableSql(table);
    }

    private static List<String> createTableSql(Table table) {
        List<String> statements = new ArrayList<>();
        statements.add(createBaseTableSql(table));
        for (EntityIndexResolver.Definition index : table.indexes()) {
            statements.add(createIndexSql(table.name(), index));
        }
        return List.copyOf(statements);
    }

    private static String createBaseTableSql(Table table) {
        StringJoiner columns = new StringJoiner(", ");
        for (Column column : table.columns()) {
            columns.add(columnDefinition(column));
        }
        return "CREATE TABLE IF NOT EXISTS " + quote(table.name()) + " (" + columns + ")";
    }

    private static String columnDefinition(Column column) {
        return quote(column.name()) + " " + column.ddl()
                + (column.primary() ? " NOT NULL PRIMARY KEY" : "");
    }

    private static String addColumnSql(String table, Column column) {
        return "ALTER TABLE " + quote(table) + " ADD COLUMN " + columnDefinition(column);
    }

    private static boolean isSimpleTable(Class<?> type, JoinTable table) {
        return table != null && table.autoSchema() && !table.value().isBlank() && table.left().isEmpty() && table.right().isEmpty()
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
            TextColumnResolver.Definition text = TextColumnResolver.resolve(field, mapping);
            boolean primary = meta != null && meta.primaryKey();
            String type = storageType(field.getType());
            if (primary && text.type() == TextTypeEnum.text) {
                throw new IllegalArgumentException("SQLite large-text column cannot be a primary key: "
                        + table.value() + "." + name);
            }
            if (primary && "TEXT".equals(type) && text.length() > 128) {
                throw new IllegalArgumentException("String primary-key textLength cannot exceed 128: "
                        + entity.getName() + "." + field.getName());
            }
            int textLength = primary && "TEXT".equals(type) && text.length() == 0 ? 128 : text.length();
            String ddl = "TEXT".equals(type) && textLength > 0 && text.type() != TextTypeEnum.text
                    ? "VARCHAR(" + textLength + ")" : type;
            var column = new Column(name, type, ddl, primary,
                    text.type() == TextTypeEnum.text, textLength);
            if (columns.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null) {
                throw new IllegalArgumentException("Duplicate SQLite column " + table.value() + "." + name);
            }
            persistentFields.putIfAbsent(field.getName(), name);
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("No persistent columns: " + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException("Composite primary keys are not supported by ORM model operations: " + entity.getName());
        }
        List<EntityIndexResolver.Definition> indexes =
                EntityIndexResolver.resolve(entity, table.value(), persistentFields);
        Map<String, Column> byName = new HashMap<>();
        columns.values().forEach(column -> byName.put(column.name().toLowerCase(Locale.ROOT), column));
        for (EntityIndexResolver.Definition index : indexes) {
            for (String name : index.columns()) {
                Column column = byName.get(name.toLowerCase(Locale.ROOT));
                if (column != null && column.largeText()) {
                    throw new IllegalArgumentException("SQLite auto-schema cannot index large-text column "
                            + table.value() + "." + column.name());
                }
            }
        }
        return new Table(table.value(), List.copyOf(columns.values()), indexes);
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

    private static Map<String, Column> ensureColumns(Connection connection, Table table,
                                                     Map<String, Column> actual,
                                                     SchemaSqlExecutor executor) throws SQLException {
        boolean changed = false;
        for (Column expected : table.columns()) {
            if (actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) continue;
            if (expected.primary()) throw incompatibleColumn(table, expected, null);
            if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new IllegalStateException("SQLite column " + table.name() + "." + expected.name()
                        + " is missing; initialize it outside a read-only transaction");
            }
            try {
                executor.execute(addColumnSql(table.name(), expected));
            } catch (SQLException error) {
                // Another application instance may have added the same column after our metadata read.
                if (!readColumns(connection, table).containsKey(expected.name().toLowerCase(Locale.ROOT))) {
                    throw error;
                }
            }
            changed = true;
        }
        if (!changed) return actual;
        Map<String, Column> updated = readColumns(connection, table);
        for (Column expected : table.columns()) {
            if (!updated.containsKey(expected.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("SQLite column was not created: "
                        + table.name() + "." + expected.name());
            }
        }
        return updated;
    }

    private static Map<String, Column> readColumns(Connection connection, Table table) throws SQLException {
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
                String declared = rows.getString("type");
                actual.put(name.toLowerCase(Locale.ROOT), new Column(name, affinity(declared), declared,
                        rows.getInt("pk") != 0, false, 0));
            }
        }
        return actual;
    }

    private static void validate(Table table, Map<String, Column> actual) {
        for (Column expected : table.columns()) {
            Column column = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (column == null || !column.type().equals(expected.type()) || column.primary() != expected.primary()) {
                throw incompatibleColumn(table, expected, column);
            }
        }
        if (actual.values().stream().filter(Column::primary).count() != table.columns().stream().filter(Column::primary).count()) {
            throw new IllegalStateException("Incompatible SQLite primary key: " + table.name());
        }
    }

    private static IllegalStateException incompatibleColumn(Table table, Column expected, Column actual) {
        return new IllegalStateException("Incompatible SQLite column " + table.name() + "." + expected.name()
                + ": expected " + expected + ", actual " + actual + "; migrate manually");
    }

    private static void ensureIndexes(Connection connection, Table table,
                                      SchemaSqlExecutor executor) throws SQLException {
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
                createIndex(table.name(), expected, executor);
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

    private static void createIndex(String table, EntityIndexResolver.Definition index,
                                    SchemaSqlExecutor executor) throws SQLException {
        executor.execute(createIndexSql(table, index));
    }

    private static String createIndexSql(String table, EntityIndexResolver.Definition index) {
        String columns = index.columns().stream().map(SqliteSchemaManager::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        return "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX IF NOT EXISTS "
                + quote(index.name()) + " ON " + quote(table) + " (" + columns + ")";
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
