package com.yulinlin.jdbc.sql.parse;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.node.AbstractCondition;
import com.yulinlin.data.core.node.AbstractMetaNode;
import com.yulinlin.data.core.node.MetaNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.enums.SqlKeys;
import com.yulinlin.jdbc.sql.SqlParamsContext;
import java.util.List;
import java.util.stream.Collectors;

/** Field names, aliases and JSON paths, registered alongside the other SQL parsers. */
public class NameParse implements IParse<AbstractMetaNode> {
    @Override public Class<AbstractMetaNode> getNodeClass() { return AbstractMetaNode.class; }

    @Override public String parse(AbstractMetaNode node, IParamsContext params, IParseManager manager) {
        String expression = (String) node.get(SqlKeys.name);
        if (expression != null) return expression;
        if (!(node instanceof MetaNode meta)) throw new NoticeException("解析key失败");
        Object value = node instanceof AbstractCondition condition ? condition.getValue() : null;
        if (value != null && (node instanceof com.yulinlin.data.core.node.base.Like
                || node instanceof com.yulinlin.data.core.node.base.LikeRight
                || node instanceof com.yulinlin.data.core.node.base.Match)) value = value.toString();
        return resolve(meta.getKey(), params, value);
    }

    public String parse(String name, IParamsContext params) { return resolve(name, params, null); }

    protected String resolve(String name, IParamsContext params, Object comparisonValue) {
        if (!AliasUtil.ok()) {
            if (!supportsHavingAlias() && params instanceof SqlParamsContext sql) {
                String expression = sql.selectExpression(name);
                return expression != null ? expression
                        : SqlJsonUtil.resolve(params.parse(params.toColumn(name)).toString(), this, comparisonValue);
            }
            return name;
        }
        String column = params.parse(params.toColumn(name)).toString();
        if (!AliasUtil.supportAlias(params)) column = writeReference(column);
        return SqlJsonUtil.resolve(column, this, comparisonValue);
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
    public String jsonExtract(String column, List<String> path, Object comparisonValue) {
        String jsonPath = "$" + path.stream().map(key -> ".\"" + key.replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"").collect(Collectors.joining());
        return "JSON_EXTRACT(" + column + ", '" + jsonPath.replace("'", "''") + "')";
    }
}
