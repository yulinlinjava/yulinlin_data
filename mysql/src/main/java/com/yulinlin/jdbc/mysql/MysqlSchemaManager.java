package com.yulinlin.jdbc.mysql;

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

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
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

/** Creates and validates conservative MySQL tables from one schema owner per table. */
public final class MysqlSchemaManager {
    private enum StorageType {
        BOOLEAN("TINYINT(1)"), INTEGER("INT"), BIGINT("BIGINT"), REAL("DOUBLE"),
        STRING("VARCHAR(255)"), TEXT("LONGTEXT");
        private final String ddl;
        StorageType(String ddl) { this.ddl = ddl; }
    }

    private record EntityType(Class<?> type, boolean underscore) { }
    private record Column(String name, StorageType type, boolean primary, int textLength,
                          boolean validateLength, String description) { }
    private record Table(String name, List<Column> columns, List<EntityIndexResolver.Definition> indexes) { }
    private record TableRef(String catalog, String schema, String name) { }
    private record ActualIndex(List<String> columns, boolean unique, boolean ascending) { }

    private final Map<EntityType, Optional<Table>> mappings = new ConcurrentHashMap<>();

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            MysqlProperties.SchemaMode mode, SchemaSqlExecutor executor) {
        Table table = table(entity, underscore);
        if (table == null || mode == MysqlProperties.SchemaMode.NONE) return false;
        try {
            TableRef actual = findTable(connection, table.name());
            if (actual == null) {
                if (mode == MysqlProperties.SchemaMode.VALIDATE) {
                    throw new IllegalStateException("MySQL table is missing: " + table.name());
                }
                create(table, executor);
                actual = findTable(connection, table.name());
                if (actual == null) throw new IllegalStateException("MySQL table was not created: " + table.name());
            }
            validate(connection, actual, table);
            ensureIndexes(connection, actual, table, mode, executor);
            return true;
        } catch (SQLException error) {
            throw new IllegalStateException("MySQL schema creation/validation failed for "
                    + entity.getName() + " (" + table.name() + "): " + error.getMessage(), error);
        }
    }

    /** Returns the exact initial DDL used by MysqlSession without touching the database. */
    public List<String> createTableSql(Class<?> entity, boolean underscore) {
        Table table = table(entity, underscore);
        return table == null ? List.of() : createTableSql(table);
    }

    private Table table(Class<?> entity, boolean underscore) {
        if (entity == null) return null;
        return mappings.computeIfAbsent(new EntityType(entity, underscore), key -> {
            JoinTable annotation = AnnotationUtil.findAnnotation(key.type(), JoinTable.class);
            return isSimpleTable(key.type(), annotation)
                    ? Optional.of(describe(key.type(), annotation, key.underscore())) : Optional.empty();
        }).orElse(null);
    }

    private static boolean isSimpleTable(Class<?> type, JoinTable table) {
        return table != null && table.autoSchema() && !table.value().isBlank()
                && table.left().isEmpty() && table.right().isEmpty() && table.on().isEmpty()
                && AnnotationUtil.findAnnotation(type, JoinTableList.class) == null
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
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                    || field.isSynthetic()) continue;
            JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
            if (mapping != null && (!mapping.exist() || !mapping.function().isEmpty())) continue;
            if (AnnotationUtil.findAnnotation(field, JoinQuery.class) != null
                    || AnnotationUtil.findAnnotation(field, JoinLazy.class) != null) continue;
            String name = aliases.toColumn(field.getName());
            if (name.contains(".")) throw new IllegalArgumentException(
                    "Qualified MySQL column in " + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            TextColumnResolver.Definition text = TextColumnResolver.resolve(field, mapping);
            if (text.type() == TextTypeEnum.varchar && text.length() > 16_383) {
                throw new IllegalArgumentException("MySQL VARCHAR textLength cannot exceed 16383 for utf8mb4: "
                        + entity.getName() + "." + field.getName());
            }
            boolean primary = meta != null && meta.primaryKey();
            StorageType type = storageType(field.getType(), text);
            if (primary && type == StorageType.TEXT) {
                throw new IllegalArgumentException("MySQL large-text column cannot be a primary key: "
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
                throw new IllegalArgumentException("Duplicate MySQL column " + table.value() + "." + name);
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
                    throw new IllegalArgumentException("MySQL auto-schema cannot index LONGTEXT column "
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
        if (type == String.class || type == char.class || type == Character.class || Date.class.isAssignableFrom(type)
                || type.isEnum() || type == BigDecimal.class || type == BigInteger.class) return StorageType.STRING;
        return StorageType.TEXT;
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
            String ddl = column.type() == StorageType.STRING && column.textLength() > 0
                    ? "VARCHAR(" + column.textLength() + ")" : column.type().ddl;
            columns.add(quote(column.name()) + " " + ddl
                    + (column.primary() ? " NOT NULL PRIMARY KEY" : "")
                    + (column.description().isBlank() ? "" : " COMMENT " + stringLiteral(column.description())));
        }
        return "CREATE TABLE IF NOT EXISTS " + quote(table.name()) + " (" + columns
                + ") ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4";
    }

    private static void create(Table table, SchemaSqlExecutor executor) throws SQLException {
        executor.execute(createBaseTableSql(table));
    }

    private static TableRef findTable(Connection connection, String name) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (var rows = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rows.next()) {
                if (name.equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
                    return new TableRef(rows.getString("TABLE_CAT"), rows.getString("TABLE_SCHEM"),
                            rows.getString("TABLE_NAME"));
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
                StorageType type = jdbcStorageType(rows.getInt("DATA_TYPE"), rows.getString("TYPE_NAME"));
                actual.put(name.toLowerCase(Locale.ROOT),
                        new Column(name, type, primary.contains(name.toLowerCase(Locale.ROOT)),
                                rows.getInt("COLUMN_SIZE"), false, ""));
            }
        }
        for (Column column : expected.columns()) {
            Column found = actual.get(column.name().toLowerCase(Locale.ROOT));
            if (found == null || found.type() != column.type() || found.primary() != column.primary()
                    || column.validateLength() && found.textLength() != column.textLength()
                    || column.primary() && column.type() == StorageType.STRING && found.textLength() > 128) {
                throw new IllegalStateException("Incompatible MySQL column " + expected.name() + "." + column.name()
                        + ": expected " + column + ", actual " + found + "; migrate manually");
            }
        }
        long expectedPrimary = expected.columns().stream().filter(Column::primary).count();
        if (primary.size() != expectedPrimary) {
            throw new IllegalStateException("Incompatible MySQL primary key: " + expected.name());
        }
    }

    private static void ensureIndexes(Connection connection, TableRef reference, Table table,
                                      MysqlProperties.SchemaMode mode, SchemaSqlExecutor executor) throws SQLException {
        if (table.indexes().isEmpty()) return;
        Map<String, ActualIndex> actual = readIndexes(connection, reference);
        boolean attempted = false;
        for (EntityIndexResolver.Definition expected : table.indexes()) {
            ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                if (mode == MysqlProperties.SchemaMode.VALIDATE) {
                    throw new IllegalStateException("MySQL index is missing: " + expected.name());
                }
                createIndex(table.name(), expected, executor);
                attempted = true;
            } else {
                validateIndex(table.name(), expected, found);
            }
        }
        if (attempted) {
            actual = readIndexes(connection, reference);
            for (EntityIndexResolver.Definition expected : table.indexes()) {
                ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
                if (found == null) throw new IllegalStateException("MySQL index was not created: " + expected.name());
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
        String sql = createIndexSql(table, index);
        try {
            executor.execute(sql);
        } catch (SQLException error) {
            // Multiple application instances may race after the metadata check. Re-read and validate below.
            if (error.getErrorCode() != 1061) throw error;
        }
    }

    private static String createIndexSql(String table, EntityIndexResolver.Definition index) {
        String columns = index.columns().stream().map(MysqlSchemaManager::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        return "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX " + quote(index.name())
                + " ON " + quote(table) + " (" + columns + ")";
    }

    private static void validateIndex(String table, EntityIndexResolver.Definition expected, ActualIndex actual) {
        if (expected.unique() != actual.unique() || !actual.ascending()
                || !sameColumns(expected.columns(), actual.columns())) {
            throw new IllegalStateException("Incompatible MySQL index " + table + "." + expected.name()
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

    private static StorageType jdbcStorageType(int type, String typeName) {
        String normalized = typeName == null ? "" : typeName.toUpperCase(Locale.ROOT);
        if (normalized.contains("TEXT") || normalized.equals("JSON")) return StorageType.TEXT;
        return switch (type) {
            case Types.BOOLEAN, Types.BIT, Types.TINYINT -> StorageType.BOOLEAN;
            case Types.SMALLINT, Types.INTEGER -> StorageType.INTEGER;
            case Types.BIGINT -> StorageType.BIGINT;
            case Types.REAL, Types.FLOAT, Types.DOUBLE -> StorageType.REAL;
            case Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR -> StorageType.STRING;
            case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> StorageType.TEXT;
            default -> null;
        };
    }

    private static void identifier(String name) {
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")
                || name.toLowerCase(Locale.ROOT).startsWith("information_schema")) {
            throw new IllegalArgumentException("MySQL schema needs a plain table/column name: " + name);
        }
    }

    private static String quote(String name) { return "`" + name.replace("`", "``") + "`"; }

    private static String stringLiteral(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }
}
