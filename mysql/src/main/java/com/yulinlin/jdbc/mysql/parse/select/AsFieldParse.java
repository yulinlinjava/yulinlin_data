package com.yulinlin.jdbc.mysql.parse.select;

import com.yulinlin.data.core.node.select.AsField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;
import com.yulinlin.jdbc.mysql.parse.MysqlJsonUtil;

public class AsFieldParse implements IParse<AsField> {


        @Override
    public String parse(AsField condition, IParamsContext params, IParseManager parseManager) {
            String name = AliasUtil.parse(condition,params) ;

            StringBuffer sql = new StringBuffer();

            sql.append(name);
            sql.append(" as ");
            sql.append("`"+condition.getAlias()+"`");



            params.getAliasContent().put(condition.getAlias(),name);


            return sql.toString();
    }
}
