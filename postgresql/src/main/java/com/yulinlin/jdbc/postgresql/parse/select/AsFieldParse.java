package com.yulinlin.jdbc.postgresql.parse.select;

import com.yulinlin.data.core.node.select.AsField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.PostgresqlFullTextOptions;
import com.yulinlin.jdbc.postgresql.parse.PostgresqlFullTextQueries;
import com.yulinlin.jdbc.sql.SqlParamsContext;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

import java.util.List;

/** Replaces the selected value with ts_headline only when the same field has a native match condition. */
public final class AsFieldParse implements IParse<AsField> {
    private final PostgresqlFullTextOptions options;
    private final com.yulinlin.jdbc.sql.parse.select.AsFieldParse fallback =
            new com.yulinlin.jdbc.sql.parse.select.AsFieldParse();

    public AsFieldParse(PostgresqlFullTextOptions options) {
        this.options = options;
    }

    @Override
    public String parse(AsField field, IParamsContext params, IParseManager parseManager) {
        if (!field.isHighlight()) return fallback.parse(field, params, parseManager);
        String column = AliasUtil.parse(field, params);
        List<String> queries = PostgresqlFullTextQueries.get(params, column);
        if (queries.isEmpty()) return fallback.parse(field, params, parseManager);
        String query = queries.size() == 1 ? queries.getFirst()
                : "(" + String.join(" || ", queries) + ")";
        String optionsParameter = params.putGetKey(options.headlineOptions());
        String expression = "ts_headline(" + options.indexConfigLiteral() + "::regconfig, COALESCE("
                + column + ", ''), " + query + ", " + optionsParameter + ")";
        String alias = SqlParamsContext.nameParse(params).selectAlias(field.getAlias());
        params.getAliasContent().put(field.getAlias(), expression);
        if (params instanceof SqlParamsContext sql) sql.selectExpression(field.getAlias(), expression);
        return expression + " as " + alias;
    }
}
