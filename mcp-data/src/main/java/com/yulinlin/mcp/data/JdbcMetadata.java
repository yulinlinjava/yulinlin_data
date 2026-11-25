package com.yulinlin.mcp.data;

import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.jdbc.session.JdbcSession;
import java.sql.*;
import java.util.*;

/**
 * JDBC 元数据（表、列、主键、数据库产品名）。只有 JDBC 数据源会调用本类，
 * 没有引入 JDBC 模块的应用不会加载它。元数据通过框架的维护连接读取，不参与业务事务。
 */
final class JdbcMetadata {
    private JdbcMetadata() { }

    private static final Set<String> TABLE_TYPES = Set.of("TABLE", "BASE TABLE", "VIEW", "MATERIALIZED VIEW", "PARTITIONED TABLE");

    @FunctionalInterface
    private interface Work<T> { T apply(Connection connection) throws SQLException; }

    private static <T> T with(String group, Work<T> work) {
        JdbcSession session = (JdbcSession) SessionUtil.route().session(group);
        try {
            return session.withMaintenanceConnection(connection -> {
                boolean marked = false;
                try { connection.setReadOnly(true); marked = true; }
                catch (SQLException unsupported) { /* 如 SQLite */ }
                try { return work.apply(connection); }
                finally {
                    try { connection.rollback(); } catch (SQLException ignored) { }
                    if (marked) try { connection.setReadOnly(false); } catch (SQLException ignored) { }
                }
            });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalArgumentException("读取数据库结构失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("读取数据库结构失败", e);
        }
    }

    static String product(String group) {
        return with(group, c -> {
            var md = c.getMetaData();
            return md.getDatabaseProductName() + " " + md.getDatabaseProductVersion();
        });
    }

    /** 不传 table 时列出表和视图；传了就返回名称包含它的表（最多 20 张）的列信息。 */
    static Map<String, Object> schema(String group, String table) {
        return with(group, c -> {
            DatabaseMetaData md = c.getMetaData();
            String catalog = c.getCatalog(), schema = schemaOf(c);
            var tables = new ArrayList<Map<String, Object>>();
            try (var rs = md.getTables(catalog, schema, "%", null)) {
                while (rs.next()) {
                    // 各驱动的类型名不同（H2 2.x 为 BASE TABLE），只保留表和视图，排除系统表、索引、序列等
                    String type = String.valueOf(rs.getString("TABLE_TYPE")).toUpperCase(Locale.ROOT);
                    if (!TABLE_TYPES.contains(type)) continue;
                    var t = new LinkedHashMap<String, Object>();
                    t.put("name", rs.getString("TABLE_NAME"));
                    t.put("type", rs.getString("TABLE_TYPE"));
                    t.put("comment", blankToNull(rs.getString("REMARKS")));
                    t.put("_schema", rs.getString("TABLE_SCHEM"));
                    tables.add(t);
                }
            }
            var out = new LinkedHashMap<String, Object>();
            out.put("database", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            if (table == null || table.isBlank()) {
                tables.forEach(t -> t.remove("_schema"));
                out.put("tables", tables.size() > 1000 ? tables.subList(0, 1000) : tables);
                out.put("truncated", tables.size() > 1000);
                out.put("hint", "传入 table 参数查看列名、类型和注释");
                return out;
            }
            String needle = table.strip().toLowerCase(Locale.ROOT);
            var matched = tables.stream().filter(t -> ((String) t.get("name")).toLowerCase(Locale.ROOT).contains(needle)).limit(20).toList();
            var details = new ArrayList<Map<String, Object>>();
            for (var t : matched) {
                String name = (String) t.get("name"), tableSchema = (String) t.get("_schema");
                var columns = new ArrayList<Map<String, Object>>();
                try (var rs = md.getColumns(catalog, tableSchema, name, "%")) {
                    while (rs.next()) {
                        var col = new LinkedHashMap<String, Object>();
                        col.put("name", rs.getString("COLUMN_NAME"));
                        col.put("type", rs.getString("TYPE_NAME"));
                        int size = rs.getInt("COLUMN_SIZE");
                        if (!rs.wasNull() && size > 0) col.put("size", size);
                        col.put("nullable", rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                        col.put("comment", blankToNull(rs.getString("REMARKS")));
                        columns.add(col);
                    }
                }
                var keys = new ArrayList<String>();
                try (var rs = md.getPrimaryKeys(catalog, tableSchema, name)) { while (rs.next()) keys.add(rs.getString("COLUMN_NAME")); }
                catch (SQLException ignored) { /* 部分驱动不支持视图主键 */ }
                var d = new LinkedHashMap<String, Object>();
                d.put("name", name);
                d.put("type", t.get("type"));
                d.put("comment", t.get("comment"));
                d.put("primaryKey", keys);
                d.put("columns", columns);
                details.add(d);
            }
            out.put("tables", details);
            if (details.isEmpty()) out.put("hint", "没有名称包含 " + table + " 的表，不传 table 可查看全部表名");
            return out;
        });
    }

    private static String schemaOf(Connection c) {
        try { return c.getSchema(); } catch (SQLException | AbstractMethodError e) { return null; }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
