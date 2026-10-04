package com.yulinlin.jdbc.mysql.parse.from;

import com.yulinlin.data.core.node.from.Join;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

public class JoinParse implements IParse<Join> {

    @Override
    public String parse(Join condition, IParamsContext params, IParseManager parseManager) {
        String left = (String) parseManager.parse(condition.getLeft(),params);
        String right =  (String) parseManager.parse(condition.getRight(),params);
        String on = (String)  parseManager.parse(condition.getOn(),params);
        String sql = left+" "+condition.getJoinEnum().name()+" join " + right;
        if(on != null){
            sql+=" on "+ on;
        }
        return sql;
    }
}
