package com.yulinlin.jdbc.mysql.parse.select;

import com.yulinlin.data.core.node.select.FunctionAsField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.MetricsWrapper;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

import java.util.Collection;

public class MetricsWrapperParse implements IParse<MetricsWrapper> {

    @Override
    public String parse(MetricsWrapper condition, IParamsContext params, IParseManager parseManager) {

        Collection<FunctionAsField> selectItems = condition.getList();
        if(selectItems.size() == 0){
            return null;
        }

        StringBuffer sql = new StringBuffer();

        for (FunctionAsField selectItem : selectItems) {
            if (sql.length() > 0) {
                sql .append( " , ");
            }
            sql.append(parseManager.parse(selectItem.getFunction(),params));
            sql.append(" as ");
            sql.append(selectItem.getAlias());



            params.getAliasContent().put(selectItem.getAlias(),AliasUtil.parse(selectItem.getFunction().getKey(),params));

        }

        return sql.toString();
    }
}
