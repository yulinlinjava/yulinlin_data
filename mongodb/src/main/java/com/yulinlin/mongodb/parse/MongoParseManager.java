package com.yulinlin.mongodb.parse;

import com.yulinlin.data.core.parse.SimpParseManager;
import com.yulinlin.mongodb.parse.base.*;
import com.yulinlin.mongodb.parse.from.StoreParse;
import com.yulinlin.mongodb.parse.group.BucketParse;
import com.yulinlin.mongodb.parse.group.DateParse;
import com.yulinlin.mongodb.parse.order.OrderParse;
import com.yulinlin.mongodb.parse.predicate.AndParse;
import com.yulinlin.mongodb.parse.predicate.OrParse;
import com.yulinlin.mongodb.parse.script.*;
import com.yulinlin.mongodb.parse.wrapper.*;

public class MongoParseManager extends SimpParseManager  {


    protected void init(){

        this.register(new MongoCountWrapperParse());
        this.register(new ExpressionNodeParse());
        this.register(new AsFieldListParse());
        this.register(new OrderParse());

        this.register(new MongoInsertFieldsParse());
        this.register(new MongoUpdateFieldsParse());


        this.register(new MongoConditionWrapperParse());

        this.register(new EqParse());
        this.register(new NeParse());
        this.register(new GteParse());
        this.register(new GtParse());
        this.register(new LteParse());
        this.register(new LtParse());
        this.register(new LikeParse());
        this.register(new LikeRightParse());
        this.register(new InParse());
        this.register(new BetweenParse());
        //this.register(new ExprMongosionParse());
        this.register(new IsNullParse());
        this.register(new NotParse());
        this.register(new AndParse());
        this.register(new OrParse());


        this.register(new StoreParse());




        this.register(new AvgFieldParse());
        this.register(new MinFieldParse());
        this.register(new MaxFieldParse());
        this.register(new SumFieldParse());
        this.register(new CountFieldParse());

        this.register(new AggregationsWrapperParse());

        this.register(new BucketParse());
        this.register(new DateParse());

        this.register(new com.yulinlin.mongodb.parse.group.AggregationsWrapperParse());


        this.register(new NilParse());

        this.register(new MongoGroupWrapperParse());
        this.register(new MongoDeleteWrapperParse());
        this.register(new MongoInsertWrapperParse());

        this.register(new MongoSelectWrapperParse());

        this.register(new MongoUpdateWrapperParse());

    }

}
