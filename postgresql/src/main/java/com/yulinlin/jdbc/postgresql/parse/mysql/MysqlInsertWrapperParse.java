package com.yulinlin.jdbc.postgresql.parse.mysql;

import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;
import com.yulinlin.jdbc.session.SqlNode;

import java.util.List;

public class MysqlInsertWrapperParse implements IParse<InsertWrapper> {




    @Override
    public ParseResult parse(InsertWrapper condition, IParamsContext params, IParseManager parseManager) {

        String sql="insert into ";

        sql += parseManager
                .parse(condition.getFrom(),params);

        List<String> columns = (  List<String>) parseManager.parse(condition.fields(),params);



        String columnSql =columns.get(0);
        String valueSql =columns.get(1);


        String model;

            model=" values ";

        if(!columnSql.equals("*")){
            sql+=" ( "+columnSql+" ) ";
        }
        sql+=model+" ( "+valueSql+" )";

        SqlNode node =   new SqlNode(sql,params.getDataBuffer());




        return new ParseResult(ParseType.insert,node,params);
    }
}
