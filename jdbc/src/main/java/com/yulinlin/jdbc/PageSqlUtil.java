package com.yulinlin.jdbc;

public class PageSqlUtil {

    public static String mysqlPageSql(int page, int size) {
        return pageSql(Mode.mysql,page,size);
    }

    public static String postgresqlPageSql(int page, int size) {
        return pageSql(Mode.postgresql,page,size);
    }
        /**
         * 生成分页 SQL 片段
         * @param page 页码（从 1 开始）
         * @param size 每页数量
         * @return SQL 片段，如 "LIMIT 10, 20" 或 "LIMIT 20 OFFSET 10"
         */
    public static String pageSql(Mode mode,int page, int size) {
        if (page <= 0 || size <= 0) {
            return "";
        }

        int offset = (page - 1) * size;
        switch (mode) {
            case mysql:
                return " LIMIT " + offset + ", " + size;
            case postgresql:
                return " LIMIT " + size + " OFFSET " + offset;
            default:
                throw new UnsupportedOperationException("Unknown DB mode: " + mode);
        }
    }

    public enum Mode {
        mysql,
        postgresql
    }

    // 可选：提供便捷切换方法

}
