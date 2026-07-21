package com.yulinlin.mongodb.parse;


import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.*;
import org.bson.BsonDocument;

public class ExpressionNodeParse implements IParse<CommandNode<BsonDocument>> {

    @Override
    public Object parse(CommandNode<BsonDocument> condition, IParamsContext params, IParseManager parseManager) {
        return new ParseResult(condition.getType(),condition.getExpression(),params);


    }
}
