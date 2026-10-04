package com.yulinlin.jdbc.postgresql.parse.predicate;

import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

public class NotParse implements IParse<Not> {

    @Override
    public String parse(Not condition, IParamsContext params, IParseManager parseManager) {
        StringBuffer sb = new StringBuffer();
        And and =  new And(condition.getList());

        String sql = (String) parseManager.parse(and,params);

        if(sql == null){
            return null;
        }
        return " not (" + sql+") ";


    }
}
