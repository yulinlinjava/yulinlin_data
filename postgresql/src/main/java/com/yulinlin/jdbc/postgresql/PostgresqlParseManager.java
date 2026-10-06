package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.postgresql.parse.NameParse;
import com.yulinlin.jdbc.postgresql.parse.PageParse;
import com.yulinlin.jdbc.postgresql.parse.group.DateParse;
import com.yulinlin.jdbc.postgresql.parse.group.IntervalParse;
import com.yulinlin.jdbc.sql.SqlParseManager;

/** Reuse shared CRUD; replace only the parsers with PostgreSQL SQL differences. */
public class PostgresqlParseManager extends SqlParseManager {
    public PostgresqlParseManager() {
        this(PostgresqlFullTextOptions.defaults());
    }

    public PostgresqlParseManager(PostgresqlFullTextOptions fullText) {
        registerFullText(java.util.Objects.requireNonNull(fullText, "fullText"));
    }

    @Override protected void init() {
        super.init();
        register(new NameParse());
        register(new PageParse());
        register(new DateParse());
        register(new IntervalParse());
    }

    private void registerFullText(PostgresqlFullTextOptions options) {
        register(new com.yulinlin.jdbc.postgresql.parse.base.MatchParse(options));
        register(new com.yulinlin.jdbc.postgresql.parse.select.AsFieldParse(options));
        register(new com.yulinlin.jdbc.postgresql.parse.statement.SqlSelectWrapperParse());
    }
}
