package com.yulinlin.jdbc.sql.parse;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.node.AbstractMetaNode;
import com.yulinlin.data.core.node.MetaNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.enums.SqlKeys;
import com.yulinlin.jdbc.sql.SqlParamsContext;

/** Entity columns and aliases. Database-specific expressions use explicit SQL entry points. */
public class NameParse implements IParse<AbstractMetaNode> {
    @Override public Class<AbstractMetaNode> getNodeClass() { return AbstractMetaNode.class; }

    @Override public String parse(AbstractMetaNode node, IParamsContext params, IParseManager manager) {
        String expression = (String) node.get(SqlKeys.name);
        if (expression != null) return expression;
        if (!(node instanceof MetaNode meta)) throw new NoticeException("解析key失败");
        return parse(meta.getKey(), params);
    }

    public String parse(String name, IParamsContext params) {
        if (name.contains("->")) {
            throw new UnsupportedOperationException("JDBC automatic JSON paths have been removed; "
                    + "use a whole column or explicit parameterized SQL: " + name);
        }
        return resolve(name, params);
    }

    protected String resolve(String name, IParamsContext params) {
        if (!AliasUtil.ok()) {
            if (!supportsHavingAlias() && params instanceof SqlParamsContext sql) {
                String expression = sql.selectExpression(name);
                return expression != null ? expression
                        : reference(params.parse(params.toColumn(name)).toString());
            }
            return alias(name);
        }
        String column = params.parse(params.toColumn(name)).toString();
        if (!AliasUtil.supportAlias(params)) column = writeReference(column);
        return reference(column);
    }

    public String reference(String name) { return name; }
    public String alias(String name) { return name; }
    public String selectAlias(String name) { return "`" + name.replace("`", "``") + "`"; }
    public String groupReference(String name, String expression) { return alias(name); }
    public boolean supportsHavingAlias() { return true; }
    public String writeReference(String column) {
        String[] parts = column.split("\\.");
        return parts[parts.length - 1];
    }
}
