package com.yulinlin.jdbc.mysql;

import com.yulinlin.jdbc.sql.SqlParseManager;
import com.yulinlin.jdbc.mysql.parse.group.DateParse;
import com.yulinlin.jdbc.mysql.parse.group.IntervalParse;

/** MySQL additions to the shared JDBC CRUD dialect. */
public class MysqlParseManager extends SqlParseManager {
    @Override protected void init() {
        super.init();
        register(new com.yulinlin.jdbc.mysql.parse.NameParse());
        register(new DateParse());
        register(new IntervalParse());
    }
}
