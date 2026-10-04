package com.yulinlin.jdbc.postgresql.parse.select;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.AsFieldListWrapper;
import com.yulinlin.jdbc.postgresql.parse.MysqlJsonUtil;

import java.util.Collection;

public class AsFieldListParse implements IParse<AsFieldListWrapper> {



        @Override
    public String parse(AsFieldListWrapper condition, IParamsContext params, IParseManager parseManager) {
            MysqlJsonUtil.push(condition);
            StringBuffer sql = new StringBuffer();
            Collection<INode> list = condition.getList();



            try {
                for (INode node : list) {
                    if (sql.length() > 0) {
                        sql.append(" , ");
                    }
                    Object sqlTel = parseManager.parse(node, params);
                    sql.append(sqlTel);
                }
            }finally {
                MysqlJsonUtil.pop();
            }




            return sql.toString();
    }
}
