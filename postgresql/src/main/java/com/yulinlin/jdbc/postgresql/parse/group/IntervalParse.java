package com.yulinlin.jdbc.postgresql.parse.group;

import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class IntervalParse implements IParse<IntervalGroup> {
    @Override public String parse(IntervalGroup node, IParamsContext params, IParseManager manager) {
        if (node.getInterval() <= 0) throw new IllegalArgumentException("Interval must be positive");
        String value = "CAST(" + AliasUtil.parse(node, params) + " AS numeric)";
        String remainder = "MOD(" + value + ", " + node.getInterval() + ")";
        // Division may round a value just below a boundary up to the next integer.
        // Exact remainder preserves [lower, upper), including negative fractions and NULL.
        return "(" + value + " - " + remainder + " + CASE WHEN " + remainder
                + " < 0 THEN 0 ELSE " + node.getInterval() + " END)";
    }
}
