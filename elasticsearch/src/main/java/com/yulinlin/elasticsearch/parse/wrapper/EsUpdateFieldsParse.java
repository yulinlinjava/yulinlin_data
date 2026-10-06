package com.yulinlin.elasticsearch.parse.wrapper;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.json.JsonData;
import com.yulinlin.data.core.anno.KeyEnum;
import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.UpdateFieldsWrapper;
import com.yulinlin.elasticsearch.parse.AliasUtil;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class EsUpdateFieldsParse implements IParse<UpdateFieldsWrapper> {

    @Override
    public Script parse(UpdateFieldsWrapper condition, IParamsContext params, IParseManager parseManager) {


        Map<String, JsonData> map = new LinkedHashMap<>();
        String   js="";


        Collection<AtomicValue> keyValueList = condition.getList();
        for (AtomicValue keyValue : keyValueList) {

            //只读字段跳过
            if(keyValue.get(KeyEnum.rw,false) == false){
                continue;
            }
            String name = AliasUtil.parse(keyValue, params);

            Object value = keyValue.getValue();

            map.put(name, JsonData.of(value));


            AtomicValue atomicValue =keyValue;
            if (atomicValue.getOperate() == AtomicValue.OperateEnum.inc) {
                js += "ctx._source." + name + " += " + "params." + name + ";";
            } else if (atomicValue.getOperate() == AtomicValue.OperateEnum.dec) {
                js += "ctx._source." + name + " -= " + "params." + name + ";";
            } else {
                js += "ctx._source." + name + " = " + "params." + name + ";";
            }

        }

        String source = js;
        Script script = new Script.Builder()
                .lang("painless")
                .source(scriptSource -> scriptSource.scriptString(source))
                .params(map)
                .build();


        return script;



    }
}
