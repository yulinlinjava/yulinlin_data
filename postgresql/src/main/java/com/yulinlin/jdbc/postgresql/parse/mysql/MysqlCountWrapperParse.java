package com.yulinlin.jdbc.postgresql.parse.mysql;


import com.yulinlin.data.core.exception.ParseException;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.AggregationsWrapper;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.postgresql.parse.AliasUtil;
import com.yulinlin.jdbc.session.SqlNode;



public class MysqlCountWrapperParse implements IParse<CountWrapper> {

    public SqlNode parse(GroupWrapper condition, IParamsContext params, IParseManager parseManager) {


        String sql="select ";
        AggregationsWrapper aggregations = (AggregationsWrapper)condition.aggregations();

        if(aggregations.getList().size() > 0){
            sql+= parseManager.parse(aggregations,params);
            sql+=" , ";
        }

        sql+=  parseManager.parse(condition.metrics(),params);



        sql +=" from "+ parseManager
                .parse(condition.getFrom(),params);
        String whereSql =(String)    parseManager.parse(condition.where().getCondition(),params);

        if(whereSql != null){
            sql+=" where " +whereSql;
        }
        String groupSql =   MysqlGroupWrapperParse.groupSql(aggregations);
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

        sql =  "select count(1) as total from ( "+ sql+" ) temp";

        SqlNode node =   new SqlNode(sql,params.getDataBuffer());


        return node;

    }

    public SqlNode parse(SelectWrapper condition, IParamsContext params, IParseManager parseManager) {




        String sql="select 1";

        sql +=" from "+ parseManager
                .parse(condition.getFrom(),params);
        String whereSql =(String)    parseManager.parse(condition.where(),params);

        if(whereSql != null){
            sql+=" where " +whereSql;
        }


        sql =  "select count(1) as total from ( "+ sql+" ) temp";

        SqlNode node =   new SqlNode(sql,params.getDataBuffer());


        return node;

    }

        @Override
    public ParseResult parse(CountWrapper countWrapper, IParamsContext params, IParseManager parseManager) {

        if(countWrapper.getWrapper() instanceof SelectWrapper){
            SelectWrapper wrapper =  (SelectWrapper)countWrapper.getWrapper();
            SqlNode node =  parse(wrapper,params,parseManager);
            return new ParseResult(ParseType.count,node,params);
        }else if(countWrapper.getWrapper() instanceof GroupWrapper){
            GroupWrapper wrapper =  (GroupWrapper)countWrapper.getWrapper();
            SqlNode node=  parse(wrapper,params,parseManager);
            return new ParseResult(ParseType.count,node,params);
        }

        throw new ParseException("不支持解析该节点:"+countWrapper.getWrapper().getClass());

    }
}
