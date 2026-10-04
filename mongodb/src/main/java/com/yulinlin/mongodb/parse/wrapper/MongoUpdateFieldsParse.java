package com.yulinlin.mongodb.parse.wrapper;

import com.yulinlin.data.core.anno.KeyEnum;
import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.UpdateFieldsWrapper;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;

import java.util.Collection;

public class MongoUpdateFieldsParse implements IParse<UpdateFieldsWrapper> {

    @Override
    public Object parse(UpdateFieldsWrapper condition, IParamsContext params, IParseManager parseManager) {


        Collection<AtomicValue> keyValueList = condition.getList();

        BsonDocument set = new BsonDocument();
        BsonDocument inc = new BsonDocument();


        for (AtomicValue keyValue : keyValueList) {

            //只读字段跳过
            if(keyValue.get(KeyEnum.rw,true) == false){
                continue;
            }

            String name = AliasUtil.parse(keyValue, params);
            Object value =params.encode( keyValue.getValue());
            AtomicValue atomicValue =keyValue;

            if (atomicValue.getOperate() == AtomicValue.OperateEnum.inc) {

                Number number = (Number)value;

                inc.append(name,BsonUtil.toBsonValue(number));

            } else if (atomicValue.getOperate() == AtomicValue.OperateEnum.dec) {
                Number number = (Number)value;
                inc.append(name,BsonUtil.toBsonValue(-number.doubleValue()));

            } else {

                set.append(name,BsonUtil.toBsonValue(value));

            }

        }

        BsonDocument document = new BsonDocument();
        if(!set.isEmpty()){
            document.append("$set",set);
        }


        if(!inc.isEmpty()){
            document.append("$inc",inc);
        }

        return document;



    }
}
