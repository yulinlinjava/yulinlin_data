package com.yulinlin.jdbc.postgresql.parse.mysql;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.from.Join;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.DeleteWrapper;
import com.yulinlin.data.lang.util.StringUtil;
import com.yulinlin.jdbc.session.SqlNode;

public class MysqlDeleteWrapperParse implements IParse<DeleteWrapper> {

    private String parse(INode node,String key){
        if(node instanceof Store){
            Store from = (Store)node;
            if(StringUtil.isNull(from.getAlias()) || from.getAlias().equals(key)){
                return from.getName();
            }
        }else if(node instanceof Join){
            Join join = (Join)node;
            String parse = parse(join.getLeft(), key);
            if(parse == null){
                parse = parse(join.getRight(), key);
            }
            return parse;
        }
        return null;
    }

    @Override
    public ParseResult parse(DeleteWrapper condition, IParamsContext params, IParseManager parseManager) {
        String sql="delete from ";

        sql += parseManager
                .parse(condition.getFrom(),params);

        String whereSql = (String)   parseManager.parse(condition.where(),params);

        if(whereSql != null){
            sql+=" where " +whereSql;
        }

        SqlNode node =   new SqlNode(sql,params.getDataBuffer());



        return new ParseResult(ParseType.delete,node,params);


    }
}
