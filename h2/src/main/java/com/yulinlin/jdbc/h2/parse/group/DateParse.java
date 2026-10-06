package com.yulinlin.jdbc.h2.parse.group;

import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class DateParse implements IParse<DateGroup> {
    @Override public String parse(DateGroup node, IParamsContext params, IParseManager manager) {
        String timestamp = "CAST(" + AliasUtil.parse(node, params) + " AS TIMESTAMP)";
        if (node.getDateType() == DateGroup.Type.quarter) {
            return "CONCAT(CONCAT(FORMATDATETIME(" + timestamp + ", 'yyyy'), '-Q'), "
                    + "CAST(EXTRACT(QUARTER FROM " + timestamp + ") AS VARCHAR))";
        }
        String format = switch (node.getDateType()) {
            case minute -> "yyyy-MM-dd HH:mm:00";
            case hour -> "yyyy-MM-dd HH";
            case day -> "yyyy-MM-dd";
            case month -> "yyyy-MM";
            case year -> "yyyy";
            case quarter -> throw new IllegalStateException("quarter handled above");
        };
        return "FORMATDATETIME(" + timestamp + ", '" + format + "')";
    }
}
