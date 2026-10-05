package com.yulinlin.jdbc.mysql.parse.group;

import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class IntervalParse implements IParse<IntervalGroup> {
    @Override public String parse(IntervalGroup node, IParamsContext params, IParseManager manager) {
        return "(FLOOR(" + AliasUtil.parse(node, params) + "/" + node.getInterval() + ") + 1 )* " + node.getInterval();
    }
}
