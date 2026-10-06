package com.yulinlin.mongodb.parse.base;

import com.mongodb.client.model.Filters;
import com.yulinlin.data.core.node.base.Match;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;

/** MongoDB compatibility fallback, matching the existing LIKE regular-expression behavior. */
public class MatchParse implements IParse<Match> {
    @Override
    public Object parse(Match condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition, params);
        String value = String.valueOf(params.encode(condition.getValue()));
        return Filters.regex(key, value).toBsonDocument();
    }
}
