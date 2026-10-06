package com.yulinlin.jdbc.postgresql;

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

/** Creates and validates conservative PostgreSQL tables from one schema owner per table. */
public final class PostgresqlSchemaManager {
    private enum StorageType {
        BOOLEAN("BOOLEAN"), INTEGER("INTEGER"), BIGINT("BIGINT"), REAL("DOUBLE PRECISION"),
        STRING("VARCHAR(255)"), TEXT("TEXT");
        private final String ddl;
        StorageType(String ddl) { this.ddl = ddl; }
    }

    private record EntityType(Class<?> type, boolean underscore) { }
    private record Column(String name, StorageType type, boolean primary, int textLength,
                          boolean validateLength, String description) { }
    private record Table(String schema, String name, List<Column> columns,
                         List<EntityIndexResolver.Definition> indexes) { }
    private record TableRef(String catalog, String schema, String name, String type) { }
    private record ActualIndex(List<String> columns, boolean unique, boolean ascending) { }

    private final Map<EntityType, Optional<Table>> mappings = new ConcurrentHashMap<>();

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            PostgresqlProperties.SchemaMode mode,
                                            SchemaSqlExecutor executor) {
        Table table = table(entity, underscore);
        if (table == null || mode == PostgresqlProperties.SchemaMode.NONE) return false;
        try {
            TableRef actual = findTable(connection, table);
            if (actual == null) {
                if (mode == PostgresqlProperties.SchemaMode.VALIDATE) {
                    throw new IllegalStateException("PostgreSQL table is missing: " + displayName(table));
                }
                create(table, executor);
                actual = findTable(connection, table);
                if (actual == null) {
                    throw new IllegalStateException("PostgreSQL table was not created: " + displayName(table));
                }
            }
            if (!"TABLE".equalsIgnoreCase(actual.type())
                    && !"PARTITIONED TABLE".equalsIgnoreCase(actual.type())) {
                throw new IllegalStateException("PostgreSQL object is not a table: " + displayName(table));
            }
            ensureColumns(connection, actual, table, mode, executor);
            validate(connection, actual, table);
            ensureIndexes(connection, actual, table, mode, executor);
            return true;
        } catch (SQLException error) {
            throw new IllegalStateException("PostgreSQL schema creation/validation failed for "
                    + entity.getName() + " (" + displayName(table) + "): " + error.getMessage(), error);
        }
    }

    /** Returns the exact initial DDL used by PostgresqlSession without touching the database. */
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
        String[] tableName = tableName(table.value());
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
                    "Qualified PostgreSQL column in " + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            TextColumnResolver.Definition text = TextColumnResolver.resolve(field, mapping);
            boolean primary = meta != null && meta.primaryKey();
            StorageType type = storageType(field.getType(), text);
            if (primary && type == StorageType.TEXT) {
                throw new IllegalArgumentException("PostgreSQL large-text column cannot be a primary key: "
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
                throw new IllegalArgumentException("Duplicate PostgreSQL column " + table.value() + "." + name);
            }
            persistentFields.putIfAbsent(field.getName(), name);
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("No persistent columns: " + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException(
                    "Composite primary keys are not supported by ORM model operations: " + entity.getName());
        }
        List<EntityIndexResolver.Definition> indexes =
                EntityIndexResolver.resolve(entity, tableName[1], persistentFields);
        Map<String, Column> byName = new HashMap<>();
        columns.values().forEach(column -> byName.put(column.name().toLowerCase(Locale.ROOT), column));
        for (EntityIndexResolver.Definition index : indexes) {
            for (String name : index.columns()) {
                Column column = byName.get(name.toLowerCase(Locale.ROOT));
                if (column != null && column.type() == StorageType.TEXT) {
                    throw new IllegalArgumentException("PostgreSQL auto-schema cannot index TEXT column "
                            + table.value() + "." + column.name());
                }
            }
        }
        return new Table(tableName[0], tableName[1], List.copyOf(columns.values()), indexes);
    }

    private static String[] tableName(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length == 1) {
            identifier(parts[0]);
            return new String[]{null, parts[0]};
        }
        if (parts.length == 2) {
            identifier(parts[0]);
            identifier(parts[1]);
            return new String[]{parts[0], parts[1]};
        }
        throw new IllegalArgumentException("PostgreSQL schema needs table or schema.table: " + value);
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
        if (type == String.class || type == char.class || type == Character.class
                || java.util.Date.class.isAssignableFrom(type) || type.isEnum()
                || type == BigDecimal.class || type == BigInteger.class) return StorageType.STRING;
        return StorageType.TEXT;
    }

    private static List<String> createTableSql(Table table) {
        List<String> statements = new ArrayList<>(createBaseTableSql(table));
        for (EntityIndexResolver.Definition index : table.indexes()) {
            statements.add(createIndexSql(table, index));
        }
        return List.copyOf(statements);
    }

    private static List<String> createBaseTableSql(Table table) {
        StringJoiner columns = new StringJoiner(", ");
        for (Column column : table.columns()) columns.add(columnDefinition(column));
        List<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE IF NOT EXISTS " + qualified(table) + " (" + columns + ")");
        for (Column column : table.columns()) {
            if (!column.description().isBlank()) statements.add(commentSql(table, column));
        }
        return List.copyOf(statements);
    }

    private static void create(Table table, SchemaSqlExecutor executor) throws SQLException {
        for (String sql : createBaseTableSql(table)) executor.execute(sql);
    }

    private static String columnDefinition(Column column) {
        String ddl = column.type() == StorageType.STRING && column.textLength() > 0
                ? "VARCHAR(" + column.textLength() + ")" : column.type().ddl;
        return quote(column.name()) + " " + ddl
                + (column.primary() ? " NOT NULL PRIMARY KEY" : "");
    }

    private static String addColumnSql(Table table, Column column) {
        return "ALTER TABLE " + qualified(table) + " ADD COLUMN IF NOT EXISTS " + columnDefinition(column);
    }

    private static String commentSql(Table table, Column column) {
        return "COMMENT ON COLUMN " + qualified(table) + "." + quote(column.name())
                + " IS " + stringLiteral(column.description());
    }

    private static TableRef findTable(Connection connection, Table table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String schema = table.schema() == null ? connection.getSchema() : table.schema();
        try (var rows = metadata.getTables(connection.getCatalog(), schema, "%", null)) {
            while (rows.next()) {
                if (table.name().equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
                    return new TableRef(rows.getString("TABLE_CAT"), rows.getString("TABLE_SCHEM"),
                            rows.getString("TABLE_NAME"), rows.getString("TABLE_TYPE"));
                }
            }
        }
        return null;
    }

    private static void ensureColumns(Connection connection, TableRef reference, Table table,
                                      PostgresqlProperties.SchemaMode mode,
                                      SchemaSqlExecutor executor) throws SQLException {
        Map<String, Column> actual = readColumns(connection, reference);
        boolean changed = false;
        for (Column expected : table.columns()) {
            if (actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) continue;
            if (expected.primary() || mode == PostgresqlProperties.SchemaMode.VALIDATE) {
                throw incompatibleColumn(table, expected, null);
            }
            executor.execute(addColumnSql(table, expected));
            if (!expected.description().isBlank()) executor.execute(commentSql(table, expected));
            changed = true;
        }
        if (changed) {
            actual = readColumns(connection, reference);
            for (Column expected : table.columns()) {
                if (!actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("PostgreSQL column was not created: "
                            + displayName(table) + "." + expected.name());
                }
            }
        }
    }

    private static void validate(Connection connection, TableRef reference, Table expected) throws SQLException {
        Set<String> primary = readPrimaryKeys(connection, reference);
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
            throw new IllegalStateException("Incompatible PostgreSQL primary key: " + displayName(expected));
        }
    }

    private static Set<String> readPrimaryKeys(Connection connection, TableRef reference) throws SQLException {
        Set<String> primary = new HashSet<>();
        try (var rows = connection.getMetaData().getPrimaryKeys(
                reference.catalog(), reference.schema(), reference.name())) {
            while (rows.next()) primary.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        return primary;
    }

    private static Map<String, Column> readColumns(Connection connection, TableRef reference) throws SQLException {
        return readColumns(connection, reference, readPrimaryKeys(connection, reference));
    }

    private static Map<String, Column> readColumns(Connection connection, TableRef reference,
                                                    Set<String> primary) throws SQLException {
        Map<String, Column> actual = new HashMap<>();
        try (var rows = connection.getMetaData().getColumns(
                reference.catalog(), reference.schema(), reference.name(), "%")) {
            while (rows.next()) {
                String name = rows.getString("COLUMN_NAME");
                actual.put(name.toLowerCase(Locale.ROOT), new Column(name,
                        jdbcStorageType(rows.getInt("DATA_TYPE"), rows.getString("TYPE_NAME")),
                        primary.contains(name.toLowerCase(Locale.ROOT)), rows.getInt("COLUMN_SIZE"), false, ""));
            }
        }
        return actual;
    }

    private static IllegalStateException incompatibleColumn(Table table, Column expected, Column actual) {
        return new IllegalStateException("Incompatible PostgreSQL column " + displayName(table) + "."
                + expected.name() + ": expected " + expected + ", actual " + actual + "; migrate manually");
    }

    private static void ensureIndexes(Connection connection, TableRef reference, Table table,
                                      PostgresqlProperties.SchemaMode mode,
                                      SchemaSqlExecutor executor) throws SQLException {
        if (table.indexes().isEmpty()) return;
        Map<String, ActualIndex> actual = readIndexes(connection, reference);
        boolean changed = false;
        for (EntityIndexResolver.Definition expected : table.indexes()) {
            ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                if (mode == PostgresqlProperties.SchemaMode.VALIDATE) {
                    throw new IllegalStateException("PostgreSQL index is missing: " + expected.name());
                }
                executor.execute(createIndexSql(table, expected));
                changed = true;
            } else {
                validateIndex(table, expected, found);
            }
        }
        if (changed) {
            actual = readIndexes(connection, reference);
            for (EntityIndexResolver.Definition expected : table.indexes()) {
                ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
                if (found == null) {
                    throw new IllegalStateException("PostgreSQL index was not created: " + expected.name());
                }
                validateIndex(table, expected, found);
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

    private static String createIndexSql(Table table, EntityIndexResolver.Definition index) {
        String columns = index.columns().stream().map(PostgresqlSchemaManager::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        return "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX IF NOT EXISTS "
                + quote(index.name()) + " ON " + qualified(table) + " (" + columns + ")";
    }

    private static void validateIndex(Table table, EntityIndexResolver.Definition expected, ActualIndex actual) {
        if (expected.unique() != actual.unique() || !actual.ascending()
                || !sameColumns(expected.columns(), actual.columns())) {
            throw new IllegalStateException("Incompatible PostgreSQL index " + displayName(table) + "."
                    + expected.name() + ": expected columns=" + expected.columns() + ", unique=" + expected.unique()
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
        String normalized = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
        if (normalized.equals("text") || normalized.equals("json") || normalized.equals("jsonb")) {
            return StorageType.TEXT;
        }
        return switch (type) {
            case Types.BOOLEAN, Types.BIT -> StorageType.BOOLEAN;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> StorageType.INTEGER;
            case Types.BIGINT -> StorageType.BIGINT;
            case Types.REAL, Types.FLOAT, Types.DOUBLE -> StorageType.REAL;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR -> StorageType.STRING;
            case Types.CLOB, Types.NCLOB -> StorageType.TEXT;
            default -> null;
        };
    }

    private static void identifier(String name) {
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")
                || name.toLowerCase(Locale.ROOT).startsWith("pg_")
                || name.equalsIgnoreCase("information_schema")) {
            throw new IllegalArgumentException("PostgreSQL schema needs a plain identifier: " + name);
        }
    }

    private static String qualified(Table table) {
        return table.schema() == null ? quote(table.name()) : quote(table.schema()) + "." + quote(table.name());
    }

    private static String displayName(Table table) {
        return table.schema() == null ? table.name() : table.schema() + "." + table.name();
    }

    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    private static String stringLiteral(String value) { return "'" + value.replace("'", "''") + "'"; }
}
