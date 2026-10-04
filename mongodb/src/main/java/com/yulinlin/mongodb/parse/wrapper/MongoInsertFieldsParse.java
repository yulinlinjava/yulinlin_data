package com.yulinlin.mongodb.parse.wrapper;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.InsertFieldsWrapper;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;

import java.util.Collection;
import java.util.Map;

public class MongoInsertFieldsParse implements IParse<InsertFieldsWrapper<?>> {

    @Override
    public Map parse(InsertFieldsWrapper<?> condition, IParamsContext params, IParseManager parseManager) {


        BsonDocument document = new BsonDocument();


        Collection<AtomicValue> list = condition.getList();


        for (AtomicValue atomicValue : list) {

            Object value =
                    params.encode(
                    atomicValue.getValue());
            String key = AliasUtil.parse(atomicValue, params);
            document.append(key,BsonUtil.toBsonValue(value));

        }



   /*     for (MongoInsertFieldsWrapper c : condition.getChildren()) {
            Object value = parseManager.parse(c,params);
            String key =c.getName();
            document.append(key,BsonUtil.toBsonValue(value));
        }
*/
        return document;


        }
}
