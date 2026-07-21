package com.yulinlin.jdbc.mysql.parse.mysql;

import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.session.SqlNode;

public class MysqlSelectWrapperParse implements IParse<SelectWrapper> {





    @Override
    public ParseResult parse(SelectWrapper condition, IParamsContext params, IParseManager parseManager) {
        String sql="select " + parseManager.parse(condition.fields(),params);

        sql +=" from "+ parseManager
                .parse(condition.getFrom(),params);


        String whereSql =(String)    parseManager.parse(condition.where(),params);

        if(whereSql != null){
            sql+=" where " +whereSql;
        }




     /*   String havingSql = (String)   parseManager.parse(condition.having().getCondition(),params);
        if(havingSql != null){
            sql+=" having " +havingSql;
        }*/

        String orderSql = (String)  parseManager.parse(condition.getOrder(),params);
        if(orderSql != null){
            sql+=" order by "+orderSql;
        }

        if(condition.getPageNumber() > 0 && condition.getPageSize() > 0){
            //String limitSql=" limit " + ((condition.getPageNumber() - 1) * condition.getPageSize()) +" , " + condition.getPageSize();

            sql+=MysqlPageSqlUtil.pageSql(condition.getPageNumber(),condition.getPageSize());
        }

        if(condition.isLock()){
            sql+=" for update";
        }

        SqlNode node =   new SqlNode(sql,params.getDataBuffer());


        return new ParseResult(ParseType.select,node,params);
    }
}
