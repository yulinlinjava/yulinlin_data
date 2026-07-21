package com.yulinlin.jdbc.mysql.parse.mysql;

import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.AbstractFieldsWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.jdbc.session.SqlNode;

public class MysqlUpdateWrapperParse implements IParse<UpdateWrapper> {

    @Override
    public ParseResult parse(UpdateWrapper condition, IParamsContext params, IParseManager parseManager) {
        String sql="update ";

        sql += parseManager
                .parse(condition.getFrom(),params);
        sql+=" set ";



        AbstractFieldsWrapper columns =  (AbstractFieldsWrapper)condition.fields();
        String columnSql=(String)parseManager.parse(columns,params);

        sql+=columnSql;

        String whereSql =  (String)  parseManager.parse(condition.where(),params);

        if(whereSql != null){
            sql+=" where " +whereSql;
        }

        SqlNode node =   new SqlNode(sql,params.getDataBuffer());



        return new ParseResult(ParseType.update,node,params);
    }
}
