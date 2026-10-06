package com.yulinlin.jdbc.h2.parse.group;

import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class IntervalParse implements IParse<IntervalGroup> {
    @Override public String parse(IntervalGroup node, IParamsContext params, IParseManager manager) {
        if (node.getInterval() <= 0) throw new IllegalArgumentException("Interval must be positive");
        return "(FLOOR(CAST(" + AliasUtil.parse(node, params) + " AS NUMERIC) / "
                + node.getInterval() + ") + 1) * " + node.getInterval();
    }
}
