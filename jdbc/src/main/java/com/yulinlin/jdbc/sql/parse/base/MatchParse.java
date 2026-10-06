package com.yulinlin.jdbc.sql.parse.base;

import com.yulinlin.data.core.node.base.Match;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

/** Portable fallback: databases without a native implementation treat match as a contains LIKE. */
public class MatchParse implements IParse<Match> {
    @Override
    public String parse(Match condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition, params);
        String value = "%" + params.encode(condition.getValue()) + "%";
        return key + " like " + params.putGetKey(value);
    }
}
