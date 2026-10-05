package com.yulinlin.jdbc.sql.parse.statement;

import com.yulinlin.data.core.node.select.GroupAsField;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.AggregationsWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.jdbc.session.SqlNode;
import com.yulinlin.jdbc.sql.parse.AliasUtil;


import java.util.List;

public class SqlGroupWrapperParse implements IParse<GroupWrapper> {


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

    public static String groupSql(AggregationsWrapper groups, IParamsContext params) {
        if (groups.getList().isEmpty()) return null;
        var names = com.yulinlin.jdbc.sql.SqlParamsContext.nameParse(params);
        var joiner = new java.util.StringJoiner(" , ");
        for (Object item : groups.getList()) {
            String alias = ((GroupAsField) item).getAlias();
            String expression = params instanceof com.yulinlin.jdbc.sql.SqlParamsContext sql
                    ? sql.selectExpression(alias) : null;
            joiner.add(names.groupReference(alias, expression));
        }
        return joiner.toString();
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

        String groupSql =   groupSql(aggregations, params);
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
            sql+=parseManager.parse(new com.yulinlin.jdbc.sql.SqlPage(condition.getPageNumber(),condition.getPageSize()), params);

        }



        SqlNode node =   new SqlNode(sql,params.getDataBuffer());



        return new ParseResult(ParseType.group,node,params);
    }
}
