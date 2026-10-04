package com.yulinlin.jdbc.postgresql.parse.select;

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
            sql.append(parseManager.parse(selectItem.getGroup(),params));
            sql.append(" as ");
            sql.append(selectItem.getAlias());



        }

        return sql.toString();
    }
}
