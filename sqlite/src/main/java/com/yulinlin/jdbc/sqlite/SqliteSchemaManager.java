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
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.jdbc.schema.EntityIndexResolver;
import com.yulinlin.jdbc.schema.SchemaSqlExecutor;
import com.yulinlin.jdbc.schema.SchemaErrors;
import com.yulinlin.jdbc.schema.TextColumnResolver;

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
                                            SchemaMode mode, SchemaSqlExecutor executor) {
        return ensureTable(connection, entity, underscore, mode, false, executor);
    }

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            SchemaMode mode, boolean alterColumnType, SchemaSqlExecutor executor) {
        executor = SchemaErrors.executor("sqlite", executor);
        Table table = table(entity, underscore);
        if (table == null || mode == SchemaMode.NONE) return false;
        Objects.requireNonNull(connection, "connection");
        try {
            if (!exists(connection, table.name())) {
                if (mode == SchemaMode.VALIDATE) {
                    throw SchemaErrors.missing("sqlite", table.name(), mode);
                }
                if (connection.isReadOnly()) {
                    throw SchemaErrors.readOnly("sqlite", table.name());
                }
                executor.execute(createBaseTableSql(table));
            }
            Map<String, Column> actual = readColumns(connection, table);
            if (alterColumnType && mode == SchemaMode.CREATE) {
                for (Column expected : table.columns()) {
                    Column found = actual.get(expected.name().toLowerCase(Locale.ROOT));
                    if (found != null && !found.type().equals(expected.type())) {
                        throw incompatibleColumn(table, expected, found, mode, true);
                    }
                }
            }
            actual = ensureColumns(connection, table, actual, mode, executor);
            validate(table, actual, mode, alterColumnType);
            ensureIndexes(connection, table, mode, executor);
            return true;
        } catch (SQLException e) {
            throw SchemaErrors.failure("sqlite", table.name() + "（实体 " + entity.getName() + "）", e);
        }
    }

    /** @deprecated Use the overload that supplies an explicit schema mode. */
    @Deprecated
    public boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                               SchemaSqlExecutor executor) {
        return ensureTable(connection, entity, underscore, SchemaMode.CREATE, executor);
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
            if (name.contains(".")) throw new IllegalArgumentException("SQLite 字段映射包含表限定名；请使用普通列名并检查 @JoinField。实体：" + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            TextColumnResolver.Definition text = TextColumnResolver.resolve(field, mapping);
            boolean primary = meta != null && meta.primaryKey();
            String type = storageType(field.getType());
            if (primary && text.type() == TextTypeEnum.text) {
                throw new IllegalArgumentException("SQLite 自动 Schema 不支持大文本主键；请改用字符串或数字主键。字段："
                        + table.value() + "." + name);
            }
            int textLength = primary && "TEXT".equals(type) && text.length() == 0
                    ? TextColumnResolver.DEFAULT_PRIMARY_KEY_LENGTH : text.length();
            String ddl = "TEXT".equals(type) && textLength > 0 && text.type() != TextTypeEnum.text
                    ? "VARCHAR(" + textLength + ")" : type;
            var column = new Column(name, type, ddl, primary,
                    text.type() == TextTypeEnum.text, textLength);
            if (columns.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null) {
                throw new IllegalArgumentException("SQLite 存在重复列映射；请修正实体字段或 @JoinField(name)。列：" + table.value() + "." + name);
            }
            persistentFields.putIfAbsent(field.getName(), name);
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("未找到持久化字段；请检查实体字段及 @JoinField(exist=false) 配置。实体：" + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException("ORM 模型操作不支持复合主键；请仅保留一个主键字段，或使用自定义 SQL 管理复合主键表。实体：" + entity.getName());
        }
        List<EntityIndexResolver.Definition> indexes =
                EntityIndexResolver.resolve(entity, table.value(), persistentFields);
        Map<String, Column> byName = new HashMap<>();
        columns.values().forEach(column -> byName.put(column.name().toLowerCase(Locale.ROOT), column));
        for (EntityIndexResolver.Definition index : indexes) {
            for (String name : index.columns()) {
                Column column = byName.get(name.toLowerCase(Locale.ROOT));
                if (column != null && column.largeText()) {
                    throw new IllegalArgumentException("SQLite 自动 Schema 不支持声明的大文本索引；请移除该 @JoinIndex 字段或手动设计索引。字段："
                            + table.value() + "." + column.name());
                }
            }
        }
        return new Table(table.value(), List.copyOf(columns.values()), indexes);
    }

    private static String storageType(Class<?> type) {
        type = com.yulinlin.jdbc.schema.JdbcScalarTypes.storageType(type);
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
                if (!"table".equals(rows.getString(1))) throw SchemaErrors.problem("sqlite", name,
                        "同名对象不是普通表", "请核对实体表名与数据库对象；视图等对象应关闭 autoSchema，不能通过开关转换为表");
                return true;
            }
        }
    }

    private static Map<String, Column> ensureColumns(Connection connection, Table table,
                                                     Map<String, Column> actual,
                                                     SchemaMode mode,
                                                     SchemaSqlExecutor executor) throws SQLException {
        boolean changed = false;
        for (Column expected : table.columns()) {
            if (actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) continue;
            if (expected.primary()) throw incompatibleColumn(table, expected, null, mode, false);
            if (mode == SchemaMode.VALIDATE) throw incompatibleColumn(table, expected, null, mode, false);
            if (connection.isReadOnly()) {
                throw SchemaErrors.readOnly("sqlite", table.name() + "." + expected.name());
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
                throw SchemaErrors.missing("sqlite", table.name() + "." + expected.name(), mode);
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
                        throw SchemaErrors.problem("sqlite", table.name() + "." + name, "映射字段对应生成列或隐藏列",
                                "请核对实体映射并手动迁移；自动 Schema 不管理生成列或隐藏列，改列开关不能解除限制");
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

    private static void validate(Table table, Map<String, Column> actual, SchemaMode mode, boolean alterColumnType) {
        for (Column expected : table.columns()) {
            Column column = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (column == null || !column.type().equals(expected.type()) || column.primary() != expected.primary()) {
                throw incompatibleColumn(table, expected, column, mode, alterColumnType);
            }
        }
        if (actual.values().stream().filter(Column::primary).count() != table.columns().stream().filter(Column::primary).count()) {
            throw SchemaErrors.primary("sqlite", table.name());
        }
    }

    private static IllegalStateException incompatibleColumn(Table table, Column expected, Column actual,
                                                            SchemaMode mode, boolean alterColumnType) {
        return SchemaErrors.column("sqlite", table.name(), diagnosticColumn(expected),
                actual == null ? null : diagnosticColumn(actual), mode, alterColumnType);
    }

    private static SchemaErrors.Column diagnosticColumn(Column column) {
        return new SchemaErrors.Column(column.name(), column.type(), column.ddl(),
                column.primary(), column.textLength());
    }

    private static void ensureIndexes(Connection connection, Table table,
                                      SchemaMode mode,
                                      SchemaSqlExecutor executor) throws SQLException {
        if (table.indexes().isEmpty()) return;
        Map<String, ActualIndex> actual = readIndexes(connection, table.name());
        boolean created = false;
        for (EntityIndexResolver.Definition expected : table.indexes()) {
            ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                if (mode == SchemaMode.VALIDATE) {
                    throw SchemaErrors.missing("sqlite", table.name() + " 索引 " + expected.name(), mode);
                }
                if (connection.isReadOnly()) {
                    throw SchemaErrors.readOnly("sqlite", table.name() + " 索引 " + expected.name());
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
                    throw SchemaErrors.missing("sqlite", table.name() + " 索引 " + expected.name(), mode);
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
            throw SchemaErrors.index("sqlite", table + "." + expected.name(), expected.columns(), expected.unique(),
                    actual.columns(), actual.unique(), actual.plain());
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
            throw new IllegalArgumentException("SQLite 表名或列名不合法；请使用受支持的普通标识符，不要传入 SQL 表达式。当前值：" + name);
        }
    }
    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
}
