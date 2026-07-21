package com.yulinlin.jdbc.postgresql.parse.mysql;

import com.yulinlin.jdbc.PageSqlUtil;

public class MysqlPageSqlUtil {

    public static String pageSql(int page, int size) {
        return PageSqlUtil.postgresqlPageSql(page,size);

    }

}
