package com.yulinlin.jdbc.mysql.parse.base;

import com.yulinlin.data.core.node.base.In;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;

public class InParse implements IParse<In> {

    @Override
    public String parse(In condition, IParamsContext params, IParseManager parseManager) {
        String key = AliasUtil.parse(condition,params) ;

        StringBuffer sb = new StringBuffer();

        for (Object row : condition.getValue()) {
            if(sb.length() > 0){
                sb.append(" , ");
            }
            sb.append( params.putGetKey(row).toString() );

        }


        String sql =  key+" in " +" ( "+sb +" ) ";
        return sql;
    }
}
