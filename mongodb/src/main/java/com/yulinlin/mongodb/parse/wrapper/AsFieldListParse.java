package com.yulinlin.mongodb.parse.wrapper;


import com.yulinlin.data.core.node.select.AsField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;

import com.yulinlin.data.core.wrapper.impl.AsFieldListWrapper;
import com.yulinlin.mongodb.parse.AliasUtil;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;

import java.util.Collection;

public class AsFieldListParse implements IParse<AsFieldListWrapper> {



        @Override
    public BsonDocument parse(AsFieldListWrapper condition, IParamsContext params, IParseManager parseManager) {


            Collection<AsField> list = condition.getList();

            BsonDocument document = new BsonDocument();

            for (AsField field : list) {
                String key =AliasUtil.parse(field.getKey(),params);

                document.append(key,BsonUtil.toBsonValue(1));
            }


            return document;
    }
}
