package com.yulinlin.jdbc.h2;

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
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Describes simple entity tables and creates or validates them with H2 metadata. */
public final class H2SchemaManager {
    private enum StorageType {
        BOOLEAN("BOOLEAN"), INTEGER("INTEGER"), BIGINT("BIGINT"), REAL("DOUBLE PRECISION"), TEXT("CHARACTER VARYING");
        private final String ddl;
        StorageType(String ddl) { this.ddl = ddl; }
    }
    private record EntityType(Class<?> type, boolean underscore) { }
    private record Column(String name, StorageType type, boolean primary) { }
    private record Table(String name, List<Column> columns, List<EntityIndexResolver.Definition> indexes) { }
    private record TableRef(String catalog, String schema, String name, String type) { }
    private record ActualIndex(List<String> columns, boolean unique, boolean ascending) { }

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

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            H2Properties.SchemaMode mode) {
        Table table = table(entity, underscore);
        if (table == null || mode == H2Properties.SchemaMode.NONE) return false;
        try {
            TableRef actual = findObject(connection, table.name());
            if (actual == null) {
                if (mode == H2Properties.SchemaMode.VALIDATE) {
                    throw new IllegalStateException("H2 table is missing: " + table.name());
                }
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalStateException("H2 table " + table.name()
                            + " is missing; initialize it outside a read-only transaction");
                }
                create(connection, table);
                actual = findObject(connection, table.name());
                if (actual == null) throw new IllegalStateException("H2 table was not created: " + table.name());
            }
            if (!"BASE TABLE".equalsIgnoreCase(actual.type()) && !"TABLE".equalsIgnoreCase(actual.type())) {
                throw new IllegalStateException("H2 object is not a table: " + table.name());
            }
            validate(connection, actual, table);
            ensureIndexes(connection, actual, table, mode);
            return true;
        } catch (SQLException error) {
            throw new IllegalStateException("H2 schema creation/validation failed for "
                    + entity.getName() + " (" + table.name() + "): " + error.getMessage(), error);
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

    private static Table describe(Class<?> entity, JoinTable table, boolean underscore) {
        identifier(table.value());
        AliasContent aliases = AliasContent.newInstance(entity, underscore);
        Map<String, Column> columns = new TreeMap<>();
        Map<String, String> persistentFields = new HashMap<>();
        for (var field : ReflectionUtil.getAllDeclaredFields(entity)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isSynthetic()) continue;
            JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
            if (mapping != null && (!mapping.exist() || !mapping.function().isEmpty())) continue;
            if (AnnotationUtil.findAnnotation(field, JoinQuery.class) != null
                    || AnnotationUtil.findAnnotation(field, JoinLazy.class) != null) continue;
            String name = aliases.toColumn(field.getName());
            if (name.contains(".")) throw new IllegalArgumentException(
                    "Qualified H2 column in " + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            Column column = new Column(name, storageType(field.getType()), meta != null && meta.primaryKey());
            if (columns.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null) {
                throw new IllegalArgumentException("Duplicate H2 column " + table.value() + "." + name);
            }
            persistentFields.putIfAbsent(field.getName(), name);
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("No persistent columns: " + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException(
                    "Composite primary keys are not supported by ORM model operations: " + entity.getName());
        }
        return new Table(table.value(), List.copyOf(columns.values()),
                EntityIndexResolver.resolve(entity, table.value(), persistentFields));
    }

    private static StorageType storageType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return StorageType.BOOLEAN;
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class) return StorageType.INTEGER;
        if (type == long.class || type == Long.class) return StorageType.BIGINT;
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return StorageType.REAL;
        }
        // Dates, enums, BigDecimal/BigInteger, byte[] and JSON values retain the shared text codecs.
        return StorageType.TEXT;
    }

    private static void create(Connection connection, Table table) throws SQLException {
        StringJoiner columns = new StringJoiner(", ");
        for (Column column : table.columns()) {
            columns.add(quote(column.name()) + " " + column.type().ddl
                    + (column.primary() ? " NOT NULL PRIMARY KEY" : ""));
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + quote(table.name()) + " (" + columns + ")");
        }
    }

    private static TableRef findObject(Connection connection, String name) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String schema = connection.getSchema();
        try (var rows = metadata.getTables(connection.getCatalog(), schema, "%", null)) {
            while (rows.next()) {
                if (name.equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
                    return new TableRef(rows.getString("TABLE_CAT"), rows.getString("TABLE_SCHEM"),
                            rows.getString("TABLE_NAME"), rows.getString("TABLE_TYPE"));
                }
            }
        }
        return null;
    }

    private static void validate(Connection connection, TableRef reference, Table expected) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> primary = new HashSet<>();
        try (var rows = metadata.getPrimaryKeys(reference.catalog(), reference.schema(), reference.name())) {
            while (rows.next()) primary.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }

        Map<String, Column> actual = new HashMap<>();
        try (var rows = metadata.getColumns(reference.catalog(), reference.schema(), reference.name(), "%")) {
            while (rows.next()) {
                String name = rows.getString("COLUMN_NAME");
                StorageType type = jdbcStorageType(rows.getInt("DATA_TYPE"));
                actual.put(name.toLowerCase(Locale.ROOT),
                        new Column(name, type, primary.contains(name.toLowerCase(Locale.ROOT))));
            }
        }
        for (Column column : expected.columns()) {
            Column found = actual.get(column.name().toLowerCase(Locale.ROOT));
            if (found == null || found.type() != column.type() || found.primary() != column.primary()) {
                throw new IllegalStateException("Incompatible H2 column " + expected.name() + "." + column.name()
                        + ": expected " + column + ", actual " + found + "; migrate manually");
            }
        }
        long expectedPrimary = expected.columns().stream().filter(Column::primary).count();
        if (primary.size() != expectedPrimary) {
            throw new IllegalStateException("Incompatible H2 primary key: " + expected.name());
        }
    }

    private static void ensureIndexes(Connection connection, TableRef reference, Table table,
                                      H2Properties.SchemaMode mode) throws SQLException {
        if (table.indexes().isEmpty()) return;
        Map<String, ActualIndex> actual = readIndexes(connection, reference);
        boolean created = false;
        for (EntityIndexResolver.Definition expected : table.indexes()) {
            ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                if (mode == H2Properties.SchemaMode.VALIDATE) {
                    throw new IllegalStateException("H2 index is missing: " + expected.name());
                }
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalStateException("H2 index " + expected.name()
                            + " is missing; initialize it outside a read-only transaction");
                }
                createIndex(connection, table.name(), expected);
                created = true;
            } else {
                validateIndex(table.name(), expected, found);
            }
        }
        if (created) {
            actual = readIndexes(connection, reference);
            for (EntityIndexResolver.Definition expected : table.indexes()) {
                ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
                if (found == null) throw new IllegalStateException("H2 index was not created: " + expected.name());
                validateIndex(table.name(), expected, found);
            }
        }
    }

    private static Map<String, ActualIndex> readIndexes(Connection connection, TableRef table) throws SQLException {
        final class Builder {
            final TreeMap<Short, String> columns = new TreeMap<>();
            boolean unique;
            boolean ascending = true;
        }
        Map<String, Builder> builders = new HashMap<>();
        try (var rows = connection.getMetaData().getIndexInfo(
                table.catalog(), table.schema(), table.name(), false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                if (name == null) continue;
                Builder builder = builders.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new Builder());
                builder.unique = !rows.getBoolean("NON_UNIQUE");
                short position = rows.getShort("ORDINAL_POSITION");
                if (position > 0) {
                    String column = rows.getString("COLUMN_NAME");
                    builder.columns.put(position, column == null ? "<expression>" : column);
                    if ("D".equalsIgnoreCase(rows.getString("ASC_OR_DESC"))) builder.ascending = false;
                }
            }
        }
        Map<String, ActualIndex> indexes = new HashMap<>();
        builders.forEach((name, builder) -> indexes.put(name,
                new ActualIndex(List.copyOf(builder.columns.values()), builder.unique, builder.ascending)));
        return indexes;
    }

    private static void createIndex(Connection connection, String table,
                                    EntityIndexResolver.Definition index) throws SQLException {
        String columns = index.columns().stream().map(H2SchemaManager::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        String sql = "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX IF NOT EXISTS "
                + quote(index.name()) + " ON " + quote(table) + " (" + columns + ")";
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void validateIndex(String table, EntityIndexResolver.Definition expected, ActualIndex actual) {
        if (expected.unique() != actual.unique() || !actual.ascending()
                || !sameColumns(expected.columns(), actual.columns())) {
            throw new IllegalStateException("Incompatible H2 index " + table + "." + expected.name()
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

    private static StorageType jdbcStorageType(int type) {
        return switch (type) {
            case Types.BOOLEAN, Types.BIT -> StorageType.BOOLEAN;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> StorageType.INTEGER;
            case Types.BIGINT -> StorageType.BIGINT;
            case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> StorageType.REAL;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> StorageType.TEXT;
            default -> null;
        };
    }

    private static void identifier(String name) {
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")
                || name.toLowerCase(Locale.ROOT).startsWith("information_schema")) {
            throw new IllegalArgumentException("H2 schema needs a plain table/column name: " + name);
        }
    }

    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}
