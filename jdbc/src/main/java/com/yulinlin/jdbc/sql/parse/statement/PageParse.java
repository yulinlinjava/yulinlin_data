package com.yulinlin.jdbc.sql.parse.statement;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.PageSqlUtil;
import com.yulinlin.jdbc.sql.SqlPage;

public class PageParse implements IParse<SqlPage> {
    @Override public String parse(SqlPage page, IParamsContext params, IParseManager manager) {
        return PageSqlUtil.mysqlPageSql(page.page(), page.size());
    }
}
