package com.yulinlin.jdbc.mysql.parse.group;

import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class DateParse implements IParse<DateGroup> {
    @Override public String parse(DateGroup node, IParamsContext params, IParseManager manager) {
        String column = AliasUtil.parse(node, params);
        if (node.getDateType() == DateGroup.Type.quarter) {
            return "CONCAT(YEAR(" + column + "), '-Q', QUARTER(" + column + "))";
        }
        String format = switch (node.getDateType()) {
            case minute -> "'%Y-%m-%d %H:%i:00'";
            case hour -> "'%Y-%m-%d %H'";
            case day -> "'%Y-%m-%d'";
            case month -> "'%Y-%m'";
            case quarter -> throw new IllegalStateException("quarter handled above");
            case year -> "'%Y'";
        };
        return "DATE_FORMAT(" + column + "," + format + ")";
    }
}
