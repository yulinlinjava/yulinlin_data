package com.yulinlin.jdbc.mysql.parse.mysql;

import com.yulinlin.jdbc.PageSqlUtil;

public class MysqlPageSqlUtil {


    /**
     * 生成分页 SQL 片段
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return SQL 片段，如 "LIMIT 10, 20" 或 "LIMIT 20 OFFSET 10"
     */
    public static String pageSql(int page, int size) {
        return PageSqlUtil.mysqlPageSql(page,size);

    }

}
