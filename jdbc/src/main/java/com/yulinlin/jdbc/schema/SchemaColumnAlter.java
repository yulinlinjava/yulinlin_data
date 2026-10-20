package com.yulinlin.jdbc.schema;

import java.sql.Connection;
import java.sql.SQLException;

/** 改列前的保守结构检查；不扫描业务数据，不管理事务，不绕过 Session 的 DDL 日志。 */
public final class SchemaColumnAlter {
    private SchemaColumnAlter() { }

    public static void check(Connection connection, String catalog, String schema,
                             String table, String column) throws SQLException {
        check("JDBC", connection, catalog, schema, table, column);
    }

    public static void check(String database, Connection connection, String catalog, String schema,
                             String table, String column) throws SQLException {
        var metadata = connection.getMetaData();
        boolean found = false;
        try (var rows = metadata.getColumns(catalog, schema, table, "%")) {
            while (rows.next()) {
                if (!table.equals(rows.getString("TABLE_NAME"))
                        || schema != null && !schema.equals(rows.getString("TABLE_SCHEM"))) continue;
                if (!column.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) continue;
                found = true;
                if ("YES".equalsIgnoreCase(rows.getString("IS_AUTOINCREMENT"))
                        || "YES".equalsIgnoreCase(rows.getString("IS_GENERATEDCOLUMN"))) {
                    throw unsupported(database, table, column, "自增列或生成列需要手动迁移");
                }
            }
        }
        if (!found) throw unsupported(database, table, column, "无法读取现有列元数据");
        try (var rows = metadata.getImportedKeys(catalog, schema, table)) {
            while (rows.next()) {
                if (column.equalsIgnoreCase(rows.getString("FKCOLUMN_NAME")))
                    throw unsupported(database, table, column, "该列引用外键，需要手动迁移");
            }
        }
        try (var rows = metadata.getExportedKeys(catalog, schema, table)) {
            while (rows.next()) {
                if (column.equalsIgnoreCase(rows.getString("PKCOLUMN_NAME")))
                    throw unsupported(database, table, column, "该列被外键引用，需要手动迁移");
            }
        }
    }

    public static IllegalStateException unsupported(String table, String column, String reason) {
        return unsupported("JDBC", table, column, reason);
    }

    public static IllegalStateException unsupported(String database, String table, String column, String reason) {
        return SchemaErrors.problem(database, table + "." + column, "不能自动修改列：" + reason,
                "请核对列属性、默认值和依赖约束，备份后由迁移脚本处理；"
                        + "开启 alter-column-type 不能解除这些保护限制，不会自动删除约束重试");
    }

    public static void execute(SchemaSqlExecutor executor, String sql) throws SQLException {
        try {
            executor.execute(sql);
        } catch (SQLException error) {
            throw new SQLException("自动修改列失败\n失败 SQL：\n" + sql + "\n数据库原始原因：" + error.getMessage()
                    + "\n处理方式：请核对数据格式、范围、精度、唯一性、默认值及约束，备份后手动处理；"
                    + "此前 DDL 可能已生效，不保证整体回滚",
                    error.getSQLState(), error.getErrorCode(), error);
        }
    }
}
