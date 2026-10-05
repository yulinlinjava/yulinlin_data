package com.yulinlin.jdbc.postgresql.parse.group;

import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class DateParse implements IParse<DateGroup> {
    @Override public String parse(DateGroup node, IParamsContext params, IParseManager manager) {
        String format = switch (node.getDateType()) {
            case minute -> "YYYY-MM-DD HH24:MI:00";
            case hour -> "YYYY-MM-DD HH24";
            case day -> "YYYY-MM-DD";
            case month, quarter -> "YYYY-MM";
            case year -> "YYYY";
        };
        return "to_char(date_trunc('" + node.getDateType().name() + "', CAST(" + AliasUtil.parse(node, params)
                + " AS timestamp)), '" + format + "')";
    }
}
