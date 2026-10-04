package com.yulinlin.mongodb.parse.script;

import com.yulinlin.data.core.node.select.FunctionAsField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.MetricsWrapper;
import org.bson.BsonDocument;
import org.bson.BsonString;

public class AggregationsWrapperParse implements IParse<MetricsWrapper<Object>> {

    @Override
    public BsonDocument[] parse(MetricsWrapper<Object> condition, IParamsContext params, IParseManager parseManager) {
        BsonDocument[] bs = {new BsonDocument(),new BsonDocument()};


        for (FunctionAsField group : condition.getList()) {
            BsonDocument project = bs[0];


            BsonDocument groupNode = (BsonDocument)parseManager.parse(group.getFunction(),params);
            String firstKey = groupNode.getFirstKey();

            if(groupNode.isString(firstKey)){
                BsonString name = groupNode.getString(firstKey);

                project.append(name.getValue().substring(1),name);

            }


            BsonDocument document = bs[1];
            document.append(group.getAlias(),groupNode);

        }

        return bs;
    }
}
