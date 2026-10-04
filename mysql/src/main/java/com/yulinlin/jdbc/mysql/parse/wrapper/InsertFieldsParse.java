package com.yulinlin.jdbc.mysql.parse.wrapper;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.InsertFieldsWrapper;
import com.yulinlin.jdbc.mysql.parse.AliasUtil;


import java.util.*;

public class InsertFieldsParse implements IParse<InsertFieldsWrapper> {

    private Map toData(InsertFieldsWrapper condition, IParamsContext params){

        Map map = new HashMap();

        try {
            Collection<AtomicValue> list = condition.getList();
            for (AtomicValue atomicValue : list) {
                Object value =atomicValue.getValue();
                String key = AliasUtil.parse(atomicValue,params) ;
                map.put(key,value);
            }


        }finally {

        }


        return map;
    }

    @Override
    public Object parse(InsertFieldsWrapper condition, IParamsContext params, IParseManager parseManager) {
            Map<String,Object> map = toData(condition, params);
            StringJoiner keys = new StringJoiner(" , ");
            StringJoiner values = new StringJoiner(" , ");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object value = params.putGetKey( entry.getValue());

                String key = entry.getKey();


                keys.add(key);

                values.add((String)value);



            }
            return Arrays.asList(keys.toString(),values.toString());
        }
}
