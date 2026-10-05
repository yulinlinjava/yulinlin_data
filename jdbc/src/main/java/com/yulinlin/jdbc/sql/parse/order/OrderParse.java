package com.yulinlin.jdbc.sql.parse.order;

import com.yulinlin.data.core.node.order.Order;
import com.yulinlin.data.core.node.order.OrderNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.sql.parse.AliasUtil;

public class OrderParse implements IParse<Order> {

    @Override
    public String parse(Order condition, IParamsContext params, IParseManager parseManager) {
        if(condition.getList().isEmpty()){
            return null;
        }
        String sql="";
        for (OrderNode item : condition.getList()) {
            if(sql.length() > 0){
                sql+=" , ";
            }
            String key =item.getKey();
            if (!com.yulinlin.jdbc.sql.SqlParamsContext.nameParse(params).supportsHavingAlias()) {
                if (params instanceof com.yulinlin.jdbc.sql.SqlParamsContext context
                        && context.selectExpression(key) != null) {
                    key = context.nameParse().alias(key);
                } else {
                    key = AliasUtil.parse(item, params);
                }
            }

            sql+= key;
            if(item.isAsc()){
                sql+=" asc ";
            }else{
                sql+=" desc ";
            }
        }
        return sql;
    }
}
