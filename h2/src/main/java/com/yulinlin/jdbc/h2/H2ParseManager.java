package com.yulinlin.jdbc.h2;

import com.yulinlin.jdbc.h2.parse.NameParse;
import com.yulinlin.jdbc.h2.parse.group.DateParse;
import com.yulinlin.jdbc.h2.parse.group.IntervalParse;
import com.yulinlin.jdbc.sql.SqlParseManager;

/** Shared JDBC CRUD plus the SQL differences required by H2. */
public class H2ParseManager extends SqlParseManager {
    @Override protected void init() {
        super.init();
        register(new NameParse());
        register(new DateParse());
        register(new IntervalParse());
    }
}
