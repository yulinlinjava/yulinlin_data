package com.yulinlin.jdbc.postgresql.parse.base;

import com.yulinlin.data.core.node.base.Match;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.postgresql.PostgresqlFullTextOptions;
import com.yulinlin.jdbc.postgresql.parse.PostgresqlFullTextMetadata;
import com.yulinlin.jdbc.postgresql.parse.PostgresqlFullTextQueries;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

/** Uses pg_jieba PostgreSQL text-search configurations for explicitly indexed entity fields. */
public final class MatchParse implements IParse<Match> {
    private final PostgresqlFullTextOptions options;
    private final com.yulinlin.jdbc.sql.parse.base.MatchParse fallback =
            new com.yulinlin.jdbc.sql.parse.base.MatchParse();

    public MatchParse(PostgresqlFullTextOptions options) {
        this.options = options;
    }

    @Override
    public String parse(Match condition, IParamsContext params, IParseManager parseManager) {
        if (!PostgresqlFullTextMetadata.enabled(params, condition.getKey())) {
            return fallback.parse(condition, params, parseManager);
        }
        String column = AliasUtil.parse(condition, params);
        String parameter = params.putGetKey(params.encode(condition.getValue()));
        String query = "websearch_to_tsquery(" + options.queryConfigLiteral()
                + "::regconfig, " + parameter + ")";
        PostgresqlFullTextQueries.add(params, column, query);
        return vector(column) + " @@ " + query;
    }

    private String vector(String column) {
        return "to_tsvector(" + options.indexConfigLiteral()
                + "::regconfig, COALESCE(" + column + ", ''))";
    }
}
