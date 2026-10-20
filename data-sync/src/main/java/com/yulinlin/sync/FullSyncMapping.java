package com.yulinlin.sync;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.jdbc.session.JdbcSession;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.*;

/** 完整单表实体的维护映射；不依赖 autoSchema 开关，不走默认值/关联/代理。 */
final class FullSyncMapping {
    enum Dialect { MYSQL, POSTGRESQL }
    record Table(String catalog, String schema, String name, String sql) { }
    final Dialect dialect;
    final Table table;
    final List<Field> fields;
    final List<String> columns;
    final String primarySignature;

    FullSyncMapping(Connection connection, JdbcSession session, Class<?> entity) throws SQLException {
        dialect = dialect(connection);
        JoinTable annotation = AnnotationUtil.findAnnotation(entity, JoinTable.class);
        if (annotation == null || annotation.value().isBlank()
                || !annotation.left().isEmpty() || !annotation.right().isEmpty() || !annotation.on().isEmpty()
                || AnnotationUtil.findAnnotation(entity, JoinTableList.class) != null)
            throw new IllegalArgumentException("全量同步要求 @JoinTable 声明的完整单表实体：" + entity.getName());
        table = table(connection, annotation.value(), dialect);
        if (table.name().equalsIgnoreCase(JdbcFullSyncJob.PROGRESS))
            throw new IllegalArgumentException("同步实体不能占用框架进度表名：" + table.name());
        ArrayList<Field> mapped = new ArrayList<>();
        Field primary = null;
        for (Field field : ReflectionUtil.getAllDeclaredFields(entity)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isSynthetic()) continue;
            JoinField column = AnnotationUtil.findAnnotation(field, JoinField.class);
            if (column != null && (!column.exist() || !column.function().isEmpty())) continue;
            if (AnnotationUtil.findAnnotation(field, JoinQuery.class) != null
                    || AnnotationUtil.findAnnotation(field, JoinLazy.class) != null) continue;
            if (AnnotationUtil.findAnnotation(field, JoinMetrics.class) != null
                    || AnnotationUtil.findAnnotation(field, JoinAggregations.class) != null)
                throw new IllegalArgumentException("全量同步不支持统计投影：" + entity.getName());
            JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
            if (meta != null && meta.primaryKey()) {
                if (primary != null) throw new IllegalArgumentException("全量同步仅支持单一主键");
                primary = field;
            } else mapped.add(field);
        }
        if (primary == null) throw new IllegalArgumentException("全量同步实体缺少 @JoinMeta(primaryKey=true)");
        Class<?> type = primary.getType();
        if (type != String.class && type != Long.class && type != long.class
                && type != Integer.class && type != int.class && type != Short.class && type != short.class
                && type != Byte.class && type != byte.class && type != java.math.BigInteger.class)
            throw new IllegalArgumentException("全量游标主键仅支持字符串或整数类型：" + type.getName());
        mapped.sort(Comparator.comparing(Field::getName));
        mapped.addFirst(primary);
        fields = List.copyOf(mapped);
        columns = fields.stream().map(session::maintenanceColumnName).toList();
        Set<String> unique = new HashSet<>();
        for (String name : columns) {
            identifier(name);
            if (!unique.add(name.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("全量同步存在重复列映射：" + name);
        }
        List<String> keys = new ArrayList<>();
        try (ResultSet rows = connection.getMetaData().getPrimaryKeys(table.catalog(), table.schema(), table.name())) {
            while (rows.next()) keys.add(rows.getString("COLUMN_NAME"));
        }
        if (keys.size() != 1 || !keys.getFirst().equalsIgnoreCase(columns.getFirst()))
            throw new IllegalStateException("全量同步要求实体主键与实际单列主键一致，表：" + table.sql() + "，现有主键：" + keys);
        // 预检字段，不会扫描数据；缺列在写入任何业务行之前报错。
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + quotedColumns() + " FROM " + table.sql() + " WHERE 1=0");
             ResultSet result = statement.executeQuery()) {
            ResultSetMetaData metadata = result.getMetaData();
            int jdbcType = metadata.getColumnType(1);
            boolean compatible = type == String.class
                    ? jdbcType == Types.VARCHAR || jdbcType == Types.CHAR
                        || jdbcType == Types.NVARCHAR || jdbcType == Types.NCHAR
                    : jdbcType == Types.BIGINT || jdbcType == Types.INTEGER || jdbcType == Types.SMALLINT
                        || jdbcType == Types.TINYINT
                        || ((jdbcType == Types.NUMERIC || jdbcType == Types.DECIMAL) && metadata.getScale(1) == 0);
            if (!compatible)
                throw new IllegalStateException("全量游标主键的实体类型与数据库类型不匹配："
                        + type.getName() + " / " + metadata.getColumnTypeName(1));
            primarySignature = jdbcType + ":" + metadata.getPrecision(1) + ":" + metadata.getScale(1);
        }
    }

    String quote(String name) { return quote(dialect, name); }
    String quotedColumns() { return String.join(", ", columns.stream().map(this::quote).toList()); }
    String primary() { return quote(columns.getFirst()); }

    static Dialect dialect(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if ("MySQL".equalsIgnoreCase(product)) return Dialect.MYSQL;
        if ("PostgreSQL".equalsIgnoreCase(product)) return Dialect.POSTGRESQL;
        throw new IllegalArgumentException("当前全量同步仅支持 MySQL/PostgreSQL，实际数据库：" + product);
    }

    static Table table(Connection connection, String value, Dialect dialect) throws SQLException {
        String[] parts = value.split("\\.", -1);
        if (parts.length < 1 || parts.length > 2) throw new IllegalArgumentException("表名只支持 table 或 schema.table：" + value);
        for (String part : parts) identifier(part);
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        if (parts.length == 2) {
            if (dialect == Dialect.MYSQL) catalog = parts[0];
            else schema = parts[0];
        }
        if (dialect == Dialect.MYSQL) schema = null;
        String name = parts[parts.length - 1];
        String qualifier = dialect == Dialect.MYSQL ? catalog : schema;
        String sql = qualifier == null ? quote(dialect, name)
                : quote(dialect, qualifier) + "." + quote(dialect, name);
        return new Table(catalog, schema, name, sql);
    }

    static String quote(Dialect dialect, String name) {
        identifier(name);
        String mark = dialect == Dialect.MYSQL ? "`" : "\"";
        return mark + name + mark;
    }

    private static void identifier(String name) {
        if (name == null || !name.matches("[\\p{L}_][\\p{L}\\p{N}_$]*"))
            throw new IllegalArgumentException("全量同步表/列标识符不合法：" + name);
    }
}
