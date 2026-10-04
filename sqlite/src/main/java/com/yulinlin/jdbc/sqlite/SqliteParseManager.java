package com.yulinlin.jdbc.sqlite;

import com.yulinlin.jdbc.sql.SqlParseManager;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;

/** SQLite shares ordinary CRUD, joins, predicates, paging and aggregate SQL with MySQL. */
public class SqliteParseManager extends SqlParseManager {
    @Override public Object parse(INode node, IParamsContext params) {
        if (node instanceof SelectWrapper select && select.isLock()) {
            throw new UnsupportedOperationException("SQLite does not support SELECT FOR UPDATE");
        }
        if (node instanceof DateGroup || node instanceof IntervalGroup) {
            throw new UnsupportedOperationException("SQLite date/interval grouping requires SQLite SQL expressions");
        }
        return super.parse(node, params);
    }
}
