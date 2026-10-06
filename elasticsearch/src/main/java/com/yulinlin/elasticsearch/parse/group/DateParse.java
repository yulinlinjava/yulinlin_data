package com.yulinlin.elasticsearch.parse.group;

import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.elasticsearch.parse.AliasUtil;

public class DateParse implements IParse<DateGroup> {

    @Override
    public Object parse(DateGroup condition, IParamsContext params, IParseManager parseManager) {

        String key =AliasUtil.parse(condition,params);

        DateGroup.Type dateType = condition.getDateType();

        CalendarInterval interval = switch (dateType) {
            case minute -> CalendarInterval.Minute;
            case hour -> CalendarInterval.Hour;
            case day -> CalendarInterval.Day;
            case month -> CalendarInterval.Month;
            case quarter -> CalendarInterval.Quarter;
            case year -> CalendarInterval.Year;
        };
        String format = switch (dateType) {
            case minute -> "yyyy-MM-dd HH:mm:00";
            case hour -> "yyyy-MM-dd HH:00:00";
            case day -> "yyyy-MM-dd";
            case month, quarter -> "yyyy-MM";
            case year -> "yyyy";
        };
        return GroupUtil.get().dateHistogram(f -> f.field(key)
                .calendarInterval(interval)
                .format(format));

    }


}
