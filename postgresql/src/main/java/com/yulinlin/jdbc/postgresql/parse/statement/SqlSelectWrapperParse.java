package com.yulinlin.jdbc.postgresql.parse.statement;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.session.SqlNode;
import com.yulinlin.jdbc.sql.SqlPage;

/** Parses WHERE first so native match expressions are available while rendering highlighted fields. */
public final class SqlSelectWrapperParse implements IParse<SelectWrapper> {
    @Override
    public ParseResult parse(SelectWrapper condition, IParamsContext params, IParseManager parseManager) {
        String where = (String) parseManager.parse(condition.where(), params);
        String fields = String.valueOf(parseManager.parse(condition.fields(), params));
        String sql = "select " + fields + " from " + parseManager.parse(condition.getFrom(), params);
        if (where != null) sql += " where " + where;
        String order = (String) parseManager.parse(condition.getOrder(), params);
        if (order != null) sql += " order by " + order;
        if (condition.getPageNumber() > 0 && condition.getPageSize() > 0) {
            sql += parseManager.parse(new SqlPage(condition.getPageNumber(), condition.getPageSize()), params);
        }
        if (condition.isLock()) sql += " for update";
        return new ParseResult(ParseType.select, new SqlNode(sql, params.getDataBuffer()), params);
    }
}
