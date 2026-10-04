package com.yulinlin.elasticsearch.parse.wrapper;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.InsertFieldsWrapper;
import com.yulinlin.elasticsearch.parse.AliasUtil;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class EsInsertFieldsParse implements IParse<InsertFieldsWrapper> {

    @Override
    public Map parse(InsertFieldsWrapper condition, IParamsContext params, IParseManager parseManager) {

        Map map = new LinkedHashMap();


        Collection<AtomicValue> list = condition.getList();


        for (AtomicValue atomicValue : list) {

            Object value = atomicValue.getValue();
            String key = AliasUtil.parse(atomicValue, params);

            map.put(key,value);

        }

        return map;


        }
}
