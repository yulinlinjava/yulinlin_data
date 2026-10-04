package com.yulinlin.jdbc.mysql.parse.order;

import com.yulinlin.data.core.node.order.Order;
import com.yulinlin.data.core.node.order.OrderNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

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
