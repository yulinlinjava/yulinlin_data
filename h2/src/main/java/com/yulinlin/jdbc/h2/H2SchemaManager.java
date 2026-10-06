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
import com.yulinlin.data.core.anno.TextTypeEnum;
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.jdbc.schema.EntityIndexResolver;
import com.yulinlin.jdbc.schema.SchemaSqlExecutor;
import com.yulinlin.jdbc.schema.TextColumnResolver;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
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
        BOOLEAN("BOOLEAN"), INTEGER("INTEGER"), BIGINT("BIGINT"), REAL("DOUBLE PRECISION"),
        STRING("CHARACTER VARYING"), TEXT("CHARACTER LARGE OBJECT");
        private final String ddl;
        StorageType(String ddl) { this.ddl = ddl; }
    }
    private record EntityType(Class<?> type, boolean underscore) { }
    private record Column(String name, StorageType type, boolean primary, int textLength,
                          boolean validateLength, String description) { }
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
                                            SchemaMode mode,
                                            SchemaSqlExecutor executor) {
        Table table = table(entity, underscore);
        if (table == null || mode == SchemaMode.NONE) return false;
        try {
            TableRef actual = findObject(connection, table.name());
            if (actual == null) {
                if (mode == SchemaMode.VALIDATE) {
                    throw new IllegalStateException("H2 table is missing: " + table.name());
                }
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalStateException("H2 table " + table.name()
                            + " is missing; initialize it outside a read-only transaction");
                }
                create(table, executor);
                actual = findObject(connection, table.name());
                if (actual == null) throw new IllegalStateException("H2 table was not created: " + table.name());
            }
            if (!"BASE TABLE".equalsIgnoreCase(actual.type()) && !"TABLE".equalsIgnoreCase(actual.type())) {
                throw new IllegalStateException("H2 object is not a table: " + table.name());
            }
            ensureColumns(connection, actual, table, mode, executor);
            validate(connection, actual, table);
            ensureIndexes(connection, actual, table, mode, executor);
            return true;
        } catch (SQLException error) {
            throw new IllegalStateException("H2 schema creation/validation failed for "
                    + entity.getName() + " (" + table.name() + "): " + error.getMessage(), error);
        }
    }

    /** Returns the exact initial DDL used by H2Session without touching the database. */
    public List<String> createTableSql(Class<?> entity, boolean underscore) {
        Table table = table(entity, underscore);
        return table == null ? List.of() : createTableSql(table);
    }

    private static boolean isSimpleTable(Class<?> type, JoinTable table) {
        return table != null && table.autoSchema() && !table.value().isBlank() && table.left().isEmpty() && table.right().isEmpty()
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
            TextColumnResolver.Definition text = TextColumnResolver.resolve(field, mapping);
            boolean primary = meta != null && meta.primaryKey();
            StorageType type = storageType(field.getType(), text);
            if (primary && type == StorageType.TEXT) {
                throw new IllegalArgumentException("H2 large-text column cannot be a primary key: "
                        + table.value() + "." + name);
            }
            if (primary && type == StorageType.STRING && text.length() > 128) {
                throw new IllegalArgumentException("String primary-key textLength cannot exceed 128: "
                        + entity.getName() + "." + field.getName());
            }
            int textLength = primary && type == StorageType.STRING && text.length() == 0 ? 128 : text.length();
            Column column = new Column(name, type, primary, textLength,
                    text.explicitLength(), text.description());
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
        List<EntityIndexResolver.Definition> indexes =
                EntityIndexResolver.resolve(entity, table.value(), persistentFields);
        Map<String, Column> byName = new HashMap<>();
        columns.values().forEach(column -> byName.put(column.name().toLowerCase(Locale.ROOT), column));
        for (EntityIndexResolver.Definition index : indexes) {
            for (String name : index.columns()) {
                Column column = byName.get(name.toLowerCase(Locale.ROOT));
                if (column != null && column.type() == StorageType.TEXT) {
                    throw new IllegalArgumentException("H2 auto-schema cannot index CLOB column "
                            + table.value() + "." + column.name());
                }
            }
        }
        return new Table(table.value(), List.copyOf(columns.values()), indexes);
    }

    private static StorageType storageType(Class<?> type, TextColumnResolver.Definition text) {
        if (text.type() == TextTypeEnum.varchar) return StorageType.STRING;
        if (text.type() == TextTypeEnum.text) return StorageType.TEXT;
        if (type == boolean.class || type == Boolean.class) return StorageType.BOOLEAN;
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class) return StorageType.INTEGER;
        if (type == long.class || type == Long.class) return StorageType.BIGINT;
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return StorageType.REAL;
        }
        // Dates, enums, BigDecimal/BigInteger, byte[] and JSON values retain the shared text codecs.
        return StorageType.STRING;
    }

    private static List<String> createTableSql(Table table) {
        List<String> statements = new ArrayList<>(createBaseTableSql(table));
        for (EntityIndexResolver.Definition index : table.indexes()) {
            statements.add(createIndexSql(table.name(), index));
        }
        return List.copyOf(statements);
    }

    private static List<String> createBaseTableSql(Table table) {
        StringJoiner columns = new StringJoiner(", ");
        for (Column column : table.columns()) {
            columns.add(columnDefinition(column));
        }
        List<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE IF NOT EXISTS " + quote(table.name()) + " (" + columns + ")");
        for (Column column : table.columns()) {
            if (!column.description().isBlank()) {
                statements.add(commentSql(table.name(), column));
            }
        }
        return List.copyOf(statements);
    }

    private static void create(Table table, SchemaSqlExecutor executor) throws SQLException {
        for (String sql : createBaseTableSql(table)) executor.execute(sql);
    }

    private static String columnDefinition(Column column) {
        String ddl = column.type() == StorageType.STRING && column.textLength() > 0
                ? "CHARACTER VARYING(" + column.textLength() + ")" : column.type().ddl;
        return quote(column.name()) + " " + ddl
                + (column.primary() ? " NOT NULL PRIMARY KEY" : "");
    }

    private static String addColumnSql(String table, Column column) {
        return "ALTER TABLE " + quote(table) + " ADD COLUMN IF NOT EXISTS " + columnDefinition(column);
    }

    private static String commentSql(String table, Column column) {
        return "COMMENT ON COLUMN " + quote(table) + "." + quote(column.name())
                + " IS " + stringLiteral(column.description());
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

    private static void ensureColumns(Connection connection, TableRef reference, Table table,
                                      SchemaMode mode,
                                      SchemaSqlExecutor executor) throws SQLException {
        Map<String, Column> actual = readColumns(connection, reference);
        boolean changed = false;
        for (Column expected : table.columns()) {
            if (actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) continue;
            if (expected.primary() || mode == SchemaMode.VALIDATE) {
                throw incompatibleColumn(table, expected, null);
            }
            if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new IllegalStateException("H2 column " + table.name() + "." + expected.name()
                        + " is missing; initialize it outside a read-only transaction");
            }
            executor.execute(addColumnSql(table.name(), expected));
            if (!expected.description().isBlank()) executor.execute(commentSql(table.name(), expected));
            changed = true;
        }
        if (changed) {
            actual = readColumns(connection, reference);
            for (Column expected : table.columns()) {
                if (!actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("H2 column was not created: "
                            + table.name() + "." + expected.name());
                }
            }
        }
    }

    private static void validate(Connection connection, TableRef reference, Table expected) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> primary = new HashSet<>();
        try (var rows = metadata.getPrimaryKeys(reference.catalog(), reference.schema(), reference.name())) {
            while (rows.next()) primary.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        Map<String, Column> actual = readColumns(connection, reference, primary);
        for (Column column : expected.columns()) {
            Column found = actual.get(column.name().toLowerCase(Locale.ROOT));
            if (found == null || found.type() != column.type() || found.primary() != column.primary()
                    || column.validateLength() && found.textLength() != column.textLength()
                    || column.primary() && column.type() == StorageType.STRING && found.textLength() > 128) {
                throw incompatibleColumn(expected, column, found);
            }
        }
        long expectedPrimary = expected.columns().stream().filter(Column::primary).count();
        if (primary.size() != expectedPrimary) {
            throw new IllegalStateException("Incompatible H2 primary key: " + expected.name());
        }
    }

    private static Map<String, Column> readColumns(Connection connection, TableRef reference) throws SQLException {
        Set<String> primary = new HashSet<>();
        try (var rows = connection.getMetaData().getPrimaryKeys(
                reference.catalog(), reference.schema(), reference.name())) {
            while (rows.next()) primary.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        return readColumns(connection, reference, primary);
    }

    private static Map<String, Column> readColumns(Connection connection, TableRef reference,
                                                    Set<String> primary) throws SQLException {
        Map<String, Column> actual = new HashMap<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (var rows = metadata.getColumns(reference.catalog(), reference.schema(), reference.name(), "%")) {
            while (rows.next()) {
                String name = rows.getString("COLUMN_NAME");
                StorageType type = jdbcStorageType(rows.getInt("DATA_TYPE"));
                actual.put(name.toLowerCase(Locale.ROOT),
                        new Column(name, type, primary.contains(name.toLowerCase(Locale.ROOT)),
                                rows.getInt("COLUMN_SIZE"), false, ""));
            }
        }
        return actual;
    }

    private static IllegalStateException incompatibleColumn(Table table, Column expected, Column actual) {
        return new IllegalStateException("Incompatible H2 column " + table.name() + "." + expected.name()
                + ": expected " + expected + ", actual " + actual + "; migrate manually");
    }

    private static void ensureIndexes(Connection connection, TableRef reference, Table table,
                                      SchemaMode mode,
                                      SchemaSqlExecutor executor) throws SQLException {
        if (table.indexes().isEmpty()) return;
        Map<String, ActualIndex> actual = readIndexes(connection, reference);
        boolean created = false;
        for (EntityIndexResolver.Definition expected : table.indexes()) {
            ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                if (mode == SchemaMode.VALIDATE) {
                    throw new IllegalStateException("H2 index is missing: " + expected.name());
                }
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalStateException("H2 index " + expected.name()
                            + " is missing; initialize it outside a read-only transaction");
                }
                createIndex(table.name(), expected, executor);
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

    private static void createIndex(String table, EntityIndexResolver.Definition index,
                                    SchemaSqlExecutor executor) throws SQLException {
        executor.execute(createIndexSql(table, index));
    }

    private static String createIndexSql(String table, EntityIndexResolver.Definition index) {
        String columns = index.columns().stream().map(H2SchemaManager::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        return "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX IF NOT EXISTS "
                + quote(index.name()) + " ON " + quote(table) + " (" + columns + ")";
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
                    Types.LONGNVARCHAR -> StorageType.STRING;
            case Types.CLOB, Types.NCLOB -> StorageType.TEXT;
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

    private static String stringLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
