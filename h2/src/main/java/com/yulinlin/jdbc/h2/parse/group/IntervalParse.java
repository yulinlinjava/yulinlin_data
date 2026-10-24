package com.yulinlin.jdbc.h2.parse.group;

import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class IntervalParse implements IParse<IntervalGroup> {
    @Override public String parse(IntervalGroup node, IParamsContext params, IParseManager manager) {
        if (node.getInterval() <= 0) throw new IllegalArgumentException("Interval must be positive");
        String value = "CAST(" + AliasUtil.parse(node, params) + " AS DECFLOAT)";
        // H2's bare NUMERIC has scale 0, and division can round across a bucket boundary.
        // Cast BOTH MOD operands: H2 returns the divisor's type, so an integer divisor
        // would discard a fractional remainder even when the dividend is DECFLOAT.
        String remainder = "MOD(" + value + ", CAST(" + node.getInterval() + " AS DECFLOAT))";
        // Equivalent to (floor(x / step) + 1) * step without rounded division.
        // Negative non-multiples already truncate to the upper bound; exact multiples
        // (including negative ones and zero) advance to the next bucket.
        return "(" + value + " - " + remainder + " + CASE WHEN " + remainder
                + " < 0 THEN 0 ELSE " + node.getInterval() + " END)";
    }
}
