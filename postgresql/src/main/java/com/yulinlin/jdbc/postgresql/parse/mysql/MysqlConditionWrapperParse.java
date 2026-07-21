package com.yulinlin.jdbc.postgresql.parse.mysql;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.ConditionManager;
import com.yulinlin.jdbc.postgresql.parse.MysqlJsonUtil;

import java.util.StringJoiner;

public class MysqlConditionWrapperParse implements IParse<ConditionManager> {

    @Override
    public Object parse(ConditionManager condition, IParamsContext params, IParseManager parseManager) {
        MysqlJsonUtil.push(condition);
        try {
            StringJoiner sb = new StringJoiner(" and ");
            String sql = (String) parseManager.parse( condition.getCondition(),params);

            if(sql != null){
                sb.add(sql);
            }

/*

            for (MysqlConditionWrapper child : children) {
                 sql = (String) parseManager.parse( child,params);

                if(sql != null){
                    sb.add(sql);
                }
            }
*/

            if(sb.length() == 0){
                return null;
            }

            return sb.toString();
        }finally {
            MysqlJsonUtil.pop();
        }

    }
}
