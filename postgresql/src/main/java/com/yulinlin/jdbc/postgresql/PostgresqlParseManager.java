package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.postgresql.parse.NameParse;
import com.yulinlin.jdbc.postgresql.parse.PageParse;
import com.yulinlin.jdbc.postgresql.parse.group.DateParse;
import com.yulinlin.jdbc.postgresql.parse.group.IntervalParse;
import com.yulinlin.jdbc.sql.SqlParseManager;

/** Reuse shared CRUD; replace only the parsers with PostgreSQL SQL differences. */
public class PostgresqlParseManager extends SqlParseManager {
    @Override protected void init() {
        super.init();
        register(new NameParse());
        register(new PageParse());
        register(new DateParse());
        register(new IntervalParse());
    }
}
