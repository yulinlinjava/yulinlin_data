package com.yulinlin.elasticsearch.parse.wrapper;

import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.yulinlin.data.core.anno.KeyEnum;
import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.*;
import com.yulinlin.data.core.wrapper.impl.AbstractFieldsWrapper;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;

import java.util.Collection;
import java.util.Map;

public class EsInsertWrapperParse implements IParse<InsertWrapper> {

    @Override
    public Object parse(InsertWrapper condition, IParamsContext params, IParseManager parseManager) {
        String index = (String) parseManager.parse(condition.getFrom(),params);
        IndexRequest.Builder request = new IndexRequest.Builder();

        AbstractFieldsWrapper columns =  (AbstractFieldsWrapper)condition.fields();
        Collection<AtomicValue> search = columns.getList();


        for (AtomicValue value : search) {
            if(value.containsKey(KeyEnum.primaryKey)){
                request.id(value.getValue().toString());
                break;
            }
        }
        Map<String, Object> stringObjectMap =(Map) parseManager.parse(condition.fields(), params);

        request.index(index);

        request.document(stringObjectMap);




        return new ParseResult(ParseType.insert,request.build(),params);


    }
}
