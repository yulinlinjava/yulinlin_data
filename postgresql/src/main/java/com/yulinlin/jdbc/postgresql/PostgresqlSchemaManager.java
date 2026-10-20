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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/** Creates and validates conservative PostgreSQL tables from one schema owner per table. */
public final class PostgresqlSchemaManager {
    private enum StorageType {
        BOOLEAN("BOOLEAN"), INTEGER("INTEGER"), BIGINT("BIGINT"), REAL("DOUBLE PRECISION"),
        DATE("DATE"), TIME("TIME WITHOUT TIME ZONE"), DATETIME("TIMESTAMP WITHOUT TIME ZONE"),
        STRING("VARCHAR(255)"), TEXT("TEXT");
        private final String ddl;
        StorageType(String ddl) { this.ddl = ddl; }
    }

    private record EntityType(Class<?> type, boolean underscore) { }
    private record Column(String name, StorageType type, boolean primary, int textLength,
                          boolean validateLength, String description, boolean fullText) { }
    private record Table(String schema, String name, List<Column> columns,
                         List<EntityIndexResolver.Definition> indexes) { }
    private record TableRef(String catalog, String schema, String name, String type) { }
    private record ActualIndex(List<String> columns, boolean unique, boolean ascending) { }

    private final Map<EntityType, Optional<Table>> mappings = new ConcurrentHashMap<>();

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            SchemaMode mode,
                                            SchemaSqlExecutor executor) {
        return ensureTable(connection, entity, underscore, mode, PostgresqlFullTextOptions.defaults(), executor);
    }

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            SchemaMode mode,
                                            PostgresqlFullTextOptions fullText,
                                            SchemaSqlExecutor executor) {
        return ensureTable(connection, entity, underscore, mode, fullText, false, executor);
    }

    public synchronized boolean ensureTable(Connection connection, Class<?> entity, boolean underscore,
                                            SchemaMode mode, PostgresqlFullTextOptions fullText,
                                            boolean alterColumnType, SchemaSqlExecutor executor) {
        executor = SchemaErrors.executor("postgresql", executor);
        Table table = table(entity, underscore);
        if (table == null || mode == SchemaMode.NONE) return false;
        try {
            if (table.columns().stream().anyMatch(Column::fullText)) {
                validateFullTextConfigurations(connection, fullText);
            }
            TableRef actual = findTable(connection, table);
            if (actual == null) {
                if (mode == SchemaMode.VALIDATE) {
                    throw SchemaErrors.missing("postgresql", displayName(table), mode);
                }
                create(table, executor);
                actual = findTable(connection, table);
                if (actual == null) {
                    throw SchemaErrors.missing("postgresql", displayName(table), mode);
                }
            }
            if (!"TABLE".equalsIgnoreCase(actual.type())
                    && !"PARTITIONED TABLE".equalsIgnoreCase(actual.type())) {
                throw SchemaErrors.problem("postgresql", displayName(table), "同名对象不是可管理的普通表",
                        "请核对 schema、实体表名和现有对象类型；视图等对象应关闭 autoSchema，不能通过改列开关转换为表");
            }
            ensureColumns(connection, actual, table, mode, executor);
            if (alterColumnType && mode == SchemaMode.CREATE) alterColumns(connection, actual, table, executor);
            validate(connection, actual, table, mode, alterColumnType);
            ensureIndexes(connection, actual, table, mode, executor);
            ensureFullTextIndexes(connection, actual, table, mode, fullText, executor);
            return true;
        } catch (SQLException error) {
            throw SchemaErrors.failure("postgresql", displayName(table) + "（实体 " + entity.getName() + "）", error);
        }
    }

    /** Returns the exact initial DDL used by PostgresqlSession without touching the database. */
    public List<String> createTableSql(Class<?> entity, boolean underscore) {
        return createTableSql(entity, underscore, PostgresqlFullTextOptions.defaults());
    }

    public List<String> createTableSql(Class<?> entity, boolean underscore,
                                       PostgresqlFullTextOptions fullText) {
        Table table = table(entity, underscore);
        return table == null ? List.of() : createTableSql(table, fullText);
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
                    "PostgreSQL 字段映射包含表限定名；请使用普通列名并检查 @JoinField。实体：" + entity.getName() + ": " + name);
            identifier(name);
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            TextColumnResolver.Definition text = TextColumnResolver.resolve(field, mapping);
            boolean primary = meta != null && meta.primaryKey();
            StorageType type = storageType(field.getType(), text);
            boolean fullText = mapping != null && mapping.fullText();
            if (fullText && type != StorageType.STRING && type != StorageType.TEXT) {
                throw new IllegalArgumentException("PostgreSQL 全文索引要求文本字段；请修正字段类型或移除 fullText=true。字段："
                        + entity.getName() + "." + field.getName());
            }
            if (primary && type == StorageType.TEXT) {
                throw new IllegalArgumentException("PostgreSQL 自动 Schema 不支持大文本主键；请改用有界字符串或数字主键。字段："
                        + table.value() + "." + name);
            }
            int textLength = primary && type == StorageType.STRING && text.length() == 0
                    ? TextColumnResolver.DEFAULT_PRIMARY_KEY_LENGTH : text.length();
            Column column = new Column(name, type, primary, textLength,
                    text.explicitLength(), text.description(), fullText);
            if (columns.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null) {
                throw new IllegalArgumentException("PostgreSQL 存在重复列映射；请修正实体字段或 @JoinField(name)。列：" + table.value() + "." + name);
            }
            persistentFields.putIfAbsent(field.getName(), name);
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("未找到持久化字段；请检查实体字段及 @JoinField(exist=false) 配置。实体：" + entity.getName());
        if (columns.values().stream().filter(Column::primary).count() > 1) {
            throw new IllegalArgumentException(
                    "ORM 模型操作不支持复合主键；请仅保留一个主键字段，或使用自定义 SQL 管理复合主键表。实体：" + entity.getName());
        }
        List<EntityIndexResolver.Definition> indexes =
                EntityIndexResolver.resolve(entity, tableName[1], persistentFields);
        Map<String, Column> byName = new HashMap<>();
        columns.values().forEach(column -> byName.put(column.name().toLowerCase(Locale.ROOT), column));
        for (EntityIndexResolver.Definition index : indexes) {
            for (String name : index.columns()) {
                Column column = byName.get(name.toLowerCase(Locale.ROOT));
                if (column != null && column.type() == StorageType.TEXT) {
                    throw new IllegalArgumentException("PostgreSQL 自动 Schema 不支持 TEXT 普通索引；请移除该 @JoinIndex 字段，按用途选择全文索引或手动管理。字段："
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
        throw new IllegalArgumentException("PostgreSQL 表名格式不合法；请使用 table 或 schema.table。当前值：" + value);
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
        if (java.util.Date.class.isAssignableFrom(type) || type == DateTime.class || type == LocalDateTime.class) {
            return StorageType.DATETIME;
        }
        if (type == String.class || type == char.class || type == Character.class || type.isEnum()
                || type == BigDecimal.class || type == BigInteger.class) return StorageType.STRING;
        return StorageType.TEXT;
    }

    private static List<String> createTableSql(Table table, PostgresqlFullTextOptions fullText) {
        List<String> statements = new ArrayList<>(createBaseTableSql(table));
        for (EntityIndexResolver.Definition index : table.indexes()) {
            statements.add(createIndexSql(table, index));
        }
        for (Column column : table.columns()) {
            if (column.fullText()) statements.add(createFullTextIndexSql(table, column, fullText));
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
                                      SchemaMode mode,
                                      SchemaSqlExecutor executor) throws SQLException {
        Map<String, Column> actual = readColumns(connection, reference);
        boolean changed = false;
        for (Column expected : table.columns()) {
            if (actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) continue;
            if (expected.primary() || mode == SchemaMode.VALIDATE) {
                throw incompatibleColumn(table, expected, null, mode, false);
            }
            executor.execute(addColumnSql(table, expected));
            if (!expected.description().isBlank()) executor.execute(commentSql(table, expected));
            changed = true;
        }
        if (changed) {
            actual = readColumns(connection, reference);
            for (Column expected : table.columns()) {
                if (!actual.containsKey(expected.name().toLowerCase(Locale.ROOT))) {
                    throw SchemaErrors.missing("postgresql", displayName(table) + "." + expected.name(), mode);
                }
            }
        }
    }

    private static void alterColumns(Connection connection, TableRef reference, Table table,
                                     SchemaSqlExecutor executor) throws SQLException {
        Map<String, Column> actual = readColumns(connection, reference);
        if (actual.values().stream().filter(Column::primary).count()
                != table.columns().stream().filter(Column::primary).count())
            throw SchemaErrors.primary("postgresql", displayName(table));
        List<String> statements = new ArrayList<>();
        for (Column expected : table.columns()) {
            Column found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null || found.primary() != expected.primary())
                throw incompatibleColumn(table, expected, found, SchemaMode.CREATE, true);
            if (found.type() == expected.type()
                    && (!expected.validateLength() || found.textLength() >= expected.textLength())) continue;
            SchemaColumnAlter.check("postgresql", connection, reference.catalog(), reference.schema(), reference.name(), found.name());
            String type = expected.type() == StorageType.STRING && expected.textLength() > 0
                    ? "VARCHAR(" + expected.textLength() + ")" : expected.type().ddl;
            // 使用元数据解析后的 schema，避免 search_path 导致修改另一张同名表。
            String sql = "ALTER TABLE " + quote(reference.schema()) + "." + quote(reference.name())
                    + " ALTER COLUMN " + quote(found.name()) + " TYPE " + type
                    + " USING CAST(" + quote(found.name()) + " AS " + type + ")";
            statements.add(sql);
        }
        // 不删除默认值/约束；数据库无法自动适配它们时直接失败，由用户手动迁移。
        for (String sql : statements) SchemaColumnAlter.execute(executor, sql);
    }

    private static void validate(Connection connection, TableRef reference, Table expected,
                                 SchemaMode mode, boolean alterColumnType) throws SQLException {
        Set<String> primary = readPrimaryKeys(connection, reference);
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
            throw SchemaErrors.primary("postgresql", displayName(expected));
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
                if (!reference.name().equals(rows.getString("TABLE_NAME"))
                        || !reference.schema().equals(rows.getString("TABLE_SCHEM"))) continue;
                String name = rows.getString("COLUMN_NAME");
                actual.put(name.toLowerCase(Locale.ROOT), new Column(name,
                        jdbcStorageType(rows.getInt("DATA_TYPE"), rows.getString("TYPE_NAME")),
                        primary.contains(name.toLowerCase(Locale.ROOT)), rows.getInt("COLUMN_SIZE"), false, "", false));
            }
        }
        return actual;
    }

    private static IllegalStateException incompatibleColumn(Table table, Column expected, Column actual,
                                                            SchemaMode mode, boolean alterColumnType) {
        return SchemaErrors.column("postgresql", displayName(table), diagnosticColumn(expected),
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
        boolean changed = false;
        for (EntityIndexResolver.Definition expected : table.indexes()) {
            ActualIndex found = actual.get(expected.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                if (mode == SchemaMode.VALIDATE) {
                    throw SchemaErrors.missing("postgresql", displayName(table) + " 索引 " + expected.name(), mode);
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
                    throw SchemaErrors.missing("postgresql", displayName(table) + " 索引 " + expected.name(), mode);
                }
                validateIndex(table, expected, found);
            }
        }
    }

    private static void ensureFullTextIndexes(Connection connection, TableRef reference, Table table,
                                              SchemaMode mode,
                                              PostgresqlFullTextOptions fullText,
                                              SchemaSqlExecutor executor) throws SQLException {
        List<Column> columns = table.columns().stream().filter(Column::fullText).toList();
        if (columns.isEmpty()) return;
        Map<String, ActualIndex> actual = readIndexes(connection, reference);
        boolean changed = false;
        for (Column column : columns) {
            String name = fullTextIndexName(table, column, fullText);
            if (actual.containsKey(name.toLowerCase(Locale.ROOT))) continue;
            if (mode == SchemaMode.VALIDATE) {
                throw SchemaErrors.missing("postgresql", displayName(table) + " 全文索引 " + name, mode);
            }
            executor.execute(createFullTextIndexSql(table, column, fullText));
            changed = true;
        }
        if (!changed) return;
        actual = readIndexes(connection, reference);
        for (Column column : columns) {
            String name = fullTextIndexName(table, column, fullText);
            if (!actual.containsKey(name.toLowerCase(Locale.ROOT))) {
                throw SchemaErrors.missing("postgresql", displayName(table) + " 全文索引 " + name, mode);
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

    private static String createFullTextIndexSql(Table table, Column column,
                                                 PostgresqlFullTextOptions fullText) {
        return "CREATE INDEX IF NOT EXISTS " + quote(fullTextIndexName(table, column, fullText))
                + " ON " + qualified(table) + " USING GIN (to_tsvector("
                + fullText.indexConfigLiteral() + "::regconfig, COALESCE("
                + quote(column.name()) + ", '')))";
    }

    private static String fullTextIndexName(Table table, Column column,
                                            PostgresqlFullTextOptions fullText) {
        String raw = ("idx_" + table.name() + "_" + column.name() + "_fts_"
                + sha256(fullText.indexConfig()).substring(0, 8)).toLowerCase(Locale.ROOT);
        if (raw.length() <= 63) return raw;
        String suffix = "_" + sha256(raw).substring(0, 8);
        return raw.substring(0, 63 - suffix.length()) + suffix;
    }

    private static void validateFullTextConfigurations(Connection connection,
                                                       PostgresqlFullTextOptions fullText) throws SQLException {
        Set<String> configs = new java.util.LinkedHashSet<>();
        configs.add(fullText.indexConfig());
        configs.add(fullText.queryConfig());
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regconfig(?)")) {
            for (String config : configs) {
                statement.clearParameters();
                statement.setString(1, config);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getString(1) == null) {
                        throw SchemaErrors.problem("postgresql", "全文检索配置 " + config, "数据库中不存在指定的分词配置",
                                "请核对 yulinlin.postgresql.full-text 配置；如使用 jieba，请先在服务器安装 pg_jieba，"
                                        + "并由有权限的管理员在目标数据库执行 CREATE EXTENSION pg_jieba");
                    }
                }
            }
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("运行环境缺少 SHA-256 算法，无法生成稳定索引名；请检查 JDK 安装及安全提供者", error);
        }
    }

    private static void validateIndex(Table table, EntityIndexResolver.Definition expected, ActualIndex actual) {
        if (expected.unique() != actual.unique() || !actual.ascending()
                || !sameColumns(expected.columns(), actual.columns())) {
            throw SchemaErrors.index("postgresql", displayName(table) + "." + expected.name(),
                    expected.columns(), expected.unique(), actual.columns(), actual.unique(), actual.ascending());
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
                || name.toLowerCase(Locale.ROOT).startsWith("pg_")
                || name.equalsIgnoreCase("information_schema")) {
            throw new IllegalArgumentException("PostgreSQL 标识符不合法或属于受限系统名称；请使用受支持的普通名称。当前值：" + name);
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
