package com.yulinlin.jdbc.sql.parse.select;

import com.yulinlin.data.core.node.select.GroupAsField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.AggregationsWrapper;

import java.util.List;

public class AggregationsWrapperParse implements IParse<AggregationsWrapper> {

    @Override
    public String parse(AggregationsWrapper condition, IParamsContext params, IParseManager parseManager) {

        List<GroupAsField> selectItems = condition.getList();
        if(selectItems.size() == 0){
            return null;
        }

        StringBuffer sql = new StringBuffer();

        for (GroupAsField selectItem : selectItems) {
            if (sql.length() > 0) {
                sql .append( " , ");
            }
            String expression = parseManager.parse(selectItem.getGroup(),params).toString();
            sql.append(expression);
            sql.append(" as ");
            sql.append(com.yulinlin.jdbc.sql.SqlParamsContext.nameParse(params).alias(selectItem.getAlias()));
            if (params instanceof com.yulinlin.jdbc.sql.SqlParamsContext context) {
                context.selectExpression(selectItem.getAlias(), expression);
            }



        }

        return sql.toString();
    }
}
