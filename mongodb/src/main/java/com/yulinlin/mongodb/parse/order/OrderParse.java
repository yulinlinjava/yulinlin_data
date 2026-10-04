package com.yulinlin.mongodb.parse.order;

import com.mongodb.client.model.Sorts;
import com.yulinlin.data.core.node.order.Order;
import com.yulinlin.data.core.node.order.OrderNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.mongodb.parse.AliasUtil;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

public class OrderParse implements IParse<Order> {

    @Override
    public BsonDocument parse(Order condition, IParamsContext params, IParseManager parseManager) {
        if(condition.getList().isEmpty()){
            return null;
        }
        BsonDocument order = new BsonDocument();



        for (OrderNode orderNode : condition.getList()) {
            String key = AliasUtil.parse(orderNode, params);

            if(orderNode.isAsc()){

                order.append(key,
                        new BsonInt32(1));
            }else {
                order.append(key,new BsonInt32(-1));
            }
        }


        return order;


    }
}
