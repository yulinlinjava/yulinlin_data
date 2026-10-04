package com.yulinlin.jdbc.mysql.parse.mysql;

import com.yulinlin.data.core.node.select.GroupAsField;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.AggregationsWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;
import com.yulinlin.jdbc.session.SqlNode;

import java.util.List;

public class MysqlGroupWrapperParse implements IParse<GroupWrapper> {


    public static String groupSql(AggregationsWrapper groups){
        StringBuffer sb =  new StringBuffer();
        List<GroupAsField> list =   groups.getList();
        if(list.isEmpty()){
            return null;
        }
        for (GroupAsField selectItem : list) {
                if(sb.length() > 0){
                    sb.append(" , ");
                }
                sb.append(selectItem.getAlias());

        }
        return  sb.toString();
    }



    @Override
    public ParseResult parse(GroupWrapper condition, IParamsContext params, IParseManager parseManager) {
        String sql="select ";
        AggregationsWrapper aggregations = (AggregationsWrapper)condition.aggregations();

        if(aggregations.getList().size() > 0){
            sql+= parseManager.parse(aggregations,params);
            sql+=" , ";
        }

        sql+=  parseManager.parse(condition.metrics(),params);


        sql +=" from "+ parseManager
                .parse(condition.getFrom(),params);


        String whereSql =(String)    parseManager.parse(condition.where(),params);

        if(whereSql != null){
            sql+=" where " +whereSql;
        }

        String groupSql =   groupSql(aggregations);
        if(groupSql != null){
            sql+=" group by " + groupSql;
        }




        try {
            AliasUtil.push(false);
            String havingSql = (String)   parseManager.parse(condition.having().getCondition(),params);
            if(havingSql != null){
                sql+=" having " +havingSql;
            }
        }finally {
            AliasUtil.pop();
        }

        String orderSql = (String)  parseManager.parse(condition.getOrder(),params);
        if(orderSql != null){
            sql+=" order by "+orderSql;
        }

        if(condition.getPageNumber() > 0){
          //  String limitSql=" limit " + ((condition.getPageNumber() - 1) * condition.getPageSize()) +" , " + condition.getPageSize();
            sql+=MysqlPageSqlUtil.pageSql(condition.getPageNumber(),condition.getPageSize());

        }



        SqlNode node =   new SqlNode(sql,params.getDataBuffer());



        return new ParseResult(ParseType.group,node,params);
    }
}
