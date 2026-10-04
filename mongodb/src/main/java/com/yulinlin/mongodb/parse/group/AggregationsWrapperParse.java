package com.yulinlin.mongodb.parse.group;

import com.yulinlin.data.core.node.select.GroupAsField;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.wrapper.impl.AggregationsWrapper;
import com.yulinlin.mongodb.parse.BsonUtil;
import org.bson.BsonDocument;
import org.bson.BsonValue;

public class AggregationsWrapperParse implements IParse<AggregationsWrapper<Object>> {

    @Override
    public BsonDocument[] parse(AggregationsWrapper<Object> condition, IParamsContext params, IParseManager parseManager) {

        BsonDocument[] bs = {new BsonDocument(),new BsonDocument()};

        for (GroupAsField group : condition.getList()) {
            String alias = group.getAlias();
            BsonValue data = (BsonValue)parseManager.parse(group.getGroup(), params);

                BsonDocument project = bs[0];
                project.append(alias,data);
                data = BsonUtil.toBsonKey(alias);

            BsonDocument document = bs[1];
            document.append(alias,data);
        }
        return bs;
    }
}
