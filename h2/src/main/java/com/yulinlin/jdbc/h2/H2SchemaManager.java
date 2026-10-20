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
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.schema.EntityIndexResolver;
import com.yulinlin.jdbc.schema.SchemaSqlExecutor;
import com.yulinlin.jdbc.schema.SchemaColumnAlter;
import com.yulinlin.jdbc.schema.SchemaErrors;
import com.yulinlin.jdbc.schema.TextColumnResolver;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
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
        DATE("DATE"), TIME("TIME"), DATETIME("TIMESTAMP"),
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
        return ensureTable(connection, entity, underscore, mode, false, executor);
    }

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            SchemaMode mode, boolean alterColumnType,
                                            SchemaSqlExecutor executor) {
        executor = SchemaErrors.executor("h2", executor);
        Table table = table(entity, underscore);
        if (table == null || mode == SchemaMode.NONE) return false;
        try {
            TableRef actual = findObject(connection, table.name());
            if (actual == null) {
                if (mode == SchemaMode.VALIDATE) {
                    throw SchemaErrors.missing("h2", table.name(), mode);
                }
                if (connection.isReadOnly()) {
                    throw SchemaErrors.readOnly("h2", table.name());
                }
                create(table, executor);
                actual = findObject(connection, table.name());
                if (actual == null) throw SchemaErrors.missing("h2", table.name(), mode);
            }
            if (!"BASE TABLE".equalsIgnoreCase(actual.type()) && !"TABLE".equalsIgnoreCase(actual.type())) {
                throw SchemaErrors.problem("h2", table.name(), "同名对象不是可管理的普通表",
                        "请核对 schema、实体表名和现有对象类型；视图等对象应关闭 autoSchema，不能通过改列开关转换为表");
            }
            ensureColumns(connection, actual, table, mode, executor);
            if (alterColumnType && mode == SchemaMode.CREATE) alterColumns(connection, actual, table, executor);
            validate(connection, actual, table, mode, alterColumnType);
            ensureIndexes(connection, actual, table, mode, executor);
            return true;
        } catch (SQLException error) {
            throw SchemaErrors.failure("h2", table.name() + "（实体 " + entity.getName() + "）", error);
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
                    "H2 字段映射包含表限定名；请使用普通列名并检查 @JoinField。实体：" + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            TextColumnResolver.Definition text = TextColumnResolver.resolve(field, mapping);
            boolean primary = meta != null && meta.primaryKey();
            StorageType type = storageType(field.getType(), text);
            if (primary && type == StorageType.TEXT) {
                throw new IllegalArgumentException("H2 大文本列不能作为自动建表主键；请改用有界字符串或数字主键。字段："
                        + table.value() + "." + name);
            }
            int textLength = primary && type == StorageType.STRING && text.length() == 0
                    ? TextColumnResolver.DEFAULT_PRIMARY_KEY_LENGTH : text.length();
            Column column = new Column(name, type, primary, textLength,
                    text.explicitLength(), text.description());
            if (columns.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null) {
                throw new IllegalArgumentException("H2 存在重复列映射；请修正实体字段或 @JoinField(name)。列：" + table.value() + "." + name);
            }
            persistentFields.putIfAbsent(field.getName(), name);
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("未找到持久化字段；请检查实体字段及 @JoinField(exist=false) 配置。实体：" + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException(
                    "ORM 模型操作不支持复合主键；请仅保留一个主键字段，或使用自定义 SQL 管理复合主键表。实体：" + entity.getName());
        }
        List<EntityIndexResolver.Definition> indexes =
                EntityIndexResolver.resolve(entity, table.value(), persistentFields);
        Map<String, Column> byName = new HashMap<>();
        columns.values().forEach(column -> byName.put(column.name().toLowerCase(Locale.ROOT), column));
        for (EntityIndexResolver.Definition index : indexes) {
            for (String name : index.columns()) {
                Column column = byName.get(name.toLowerCase(Locale.ROOT));
                if (column != null && column.type() == StorageType.TEXT) {
                    throw new IllegalArgumentException("H2 自动 Schema 不支持 CLOB 普通索引；请移除该 @JoinIndex 字段或手动设计索引。字段："
                            + table.value() + "." + column.name());
                }
            }
        }
        return new Table(table.value(), List.copyOf(columns.values()), indexes);
    }

    private static StorageType storageType(Class<?> type, TextColumnResolver.Definition text) {
        type = com.yulinlin.jdbc.schema.JdbcScalarTypes.storageType(type);
        if (text.type() == TextTypeEnum.varchar) return StorageType.STRING;
        if (text.type() == TextTypeEnum.text) return StorageType.TEXT;
        if (type == boolean.class || type == Boolean.class) return StorageType.BOOLEAN;
        if (type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class) return StorageType.INTEGER;
        if (type == long.class || type == Long.class) return StorageType.BIGINT;
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return StorageType.REAL;
        }
        if (type == LocalDate.class) return StorageType.DATE;
        if (type == LocalTime.class) return StorageType.TIME;
        if (Date.class.isAssignableFrom(type) || type == DateTime.class || type == LocalDateTime.class) {
            return StorageType.DATETIME;
        }
        // Enums, BigDecimal/BigInteger, byte[] and JSON values retain the shared text codecs.
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
                throw incompatibleColumn(table, expected, null, mode, false);
            }
            if (connection.isReadOnly()) {
                throw SchemaErrors.readOnly("h2", table.name() + "." + expected.name());
            }
            executor.execute(addColumnSql(table.name(), expected));
            if (!expected.description().isBlank()) executor.execute(commentSql(table.name(), expected));
            changed = true;
        }
        if (changed) {
            actual = readColumns(connection, reference);
            for (Column expected : table.columns()) {
                if (!actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) {
                    throw SchemaErrors.missing("h2", table.name() + "." + expected.name(), mode);
                }
            }
        }
    }

    private static void alterColumns(Connection connection, TableRef reference, Table table,
                                     SchemaSqlExecutor executor) throws SQLException {
        Map<String, Column> actual = readColumns(connection, reference);
        if (actual.values().stream().filter(Column::primary).count()
                != table.columns().stream().filter(Column::primary).count())
            throw SchemaErrors.primary("h2", table.name());
        List<String> statements = new ArrayList<>();
        for (Column expected : table.columns()) {
            Column found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null || found.primary() != expected.primary())
                throw incompatibleColumn(table, expected, found, SchemaMode.CREATE, true);
            if (found.type() == expected.type()
                    && (!expected.validateLength() || found.textLength() >= expected.textLength())) continue;
            SchemaColumnAlter.check("h2", connection, reference.catalog(), reference.schema(), reference.name(), found.name());
            String type = expected.type() == StorageType.STRING && expected.textLength() > 0
                    ? "CHARACTER VARYING(" + expected.textLength() + ")" : expected.type().ddl;
            statements.add("ALTER TABLE " + quote(reference.schema()) + "." + quote(reference.name())
                    + " ALTER COLUMN " + quote(found.name()) + " SET DATA TYPE " + type);
        }
        for (String sql : statements) SchemaColumnAlter.execute(executor, sql);
    }

    private static void validate(Connection connection, TableRef reference, Table expected,
                                 SchemaMode mode, boolean alterColumnType) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> primary = new HashSet<>();
        try (var rows = metadata.getPrimaryKeys(reference.catalog(), reference.schema(), reference.name())) {
            while (rows.next()) primary.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        Map<String, Column> actual = readColumns(connection, reference, primary);
        for (Column column : expected.columns()) {
            Column found = actual.get(column.name().toLowerCase(Locale.ROOT));
            // 显式长度是所需容量：已有列更长可兼容，不自动缩短或扩容。
            if (found == null || found.type() != column.type() || found.primary() != column.primary()
                    || column.validateLength() && found.textLength() < column.textLength()) {
                throw incompatibleColumn(expected, column, found, mode, alterColumnType);
            }
        }
        long expectedPrimary = expected.columns().stream().filter(Column::primary).count();
        if (primary.size() != expectedPrimary) {
            throw SchemaErrors.primary("h2", expected.name());
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
                if (!reference.name().equals(rows.getString("TABLE_NAME"))
                        || !reference.schema().equals(rows.getString("TABLE_SCHEM"))) continue;
                String name = rows.getString("COLUMN_NAME");
                StorageType type = jdbcStorageType(rows.getInt("DATA_TYPE"));
                actual.put(name.toLowerCase(Locale.ROOT),
                        new Column(name, type, primary.contains(name.toLowerCase(Locale.ROOT)),
                                rows.getInt("COLUMN_SIZE"), false, ""));
            }
        }
        return actual;
    }

    private static IllegalStateException incompatibleColumn(Table table, Column expected, Column actual,
                                                            SchemaMode mode, boolean alterColumnType) {
        return SchemaErrors.column("h2", table.name(), diagnosticColumn(expected),
                actual == null ? null : diagnosticColumn(actual), mode, alterColumnType);
    }

    private static SchemaErrors.Column diagnosticColumn(Column column) {
        return new SchemaErrors.Column(column.name(), column.type().name(),
                SchemaErrors.textType(column.type().ddl, column.textLength()), column.primary(), column.textLength());
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
                    throw SchemaErrors.missing("h2", table.name() + " 索引 " + expected.name(), mode);
                }
                if (connection.isReadOnly()) {
                    throw SchemaErrors.readOnly("h2", table.name() + " 索引 " + expected.name());
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
                if (found == null) throw SchemaErrors.missing("h2", table.name() + " 索引 " + expected.name(), mode);
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
            throw SchemaErrors.index("h2", table + "." + expected.name(), expected.columns(), expected.unique(),
                    actual.columns(), actual.unique(), actual.ascending());
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
            case Types.DATE -> StorageType.DATE;
            case Types.TIME -> StorageType.TIME;
            case Types.TIMESTAMP -> StorageType.DATETIME;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
                    Types.LONGNVARCHAR -> StorageType.STRING;
            case Types.CLOB, Types.NCLOB -> StorageType.TEXT;
            default -> null;
        };
    }

    private static void identifier(String name) {
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")
                || name.toLowerCase(Locale.ROOT).startsWith("information_schema")) {
            throw new IllegalArgumentException("H2 表名或列名不合法；请使用受支持的普通标识符，不要传入 SQL 表达式。当前值：" + name);
        }
    }

    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }

    private static String stringLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
