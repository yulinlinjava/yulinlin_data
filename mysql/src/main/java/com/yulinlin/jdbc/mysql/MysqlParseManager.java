package com.yulinlin.jdbc.mysql;


import com.yulinlin.data.core.parse.SimpParseManager;
import com.yulinlin.jdbc.mysql.parse.ExpressionNodeParse;
import com.yulinlin.jdbc.mysql.parse.base.*;
import com.yulinlin.jdbc.mysql.parse.from.JoinParse;
import com.yulinlin.jdbc.mysql.parse.from.StoreParse;
import com.yulinlin.jdbc.mysql.parse.group.BucketParse;
import com.yulinlin.jdbc.mysql.parse.group.DateParse;
import com.yulinlin.jdbc.mysql.parse.group.IntervalParse;
import com.yulinlin.jdbc.mysql.parse.mysql.*;
import com.yulinlin.jdbc.mysql.parse.order.OrderParse;
import com.yulinlin.jdbc.mysql.parse.predicate.AndParse;
import com.yulinlin.jdbc.mysql.parse.predicate.NotParse;
import com.yulinlin.jdbc.mysql.parse.predicate.OrParse;
import com.yulinlin.jdbc.mysql.parse.script.*;
import com.yulinlin.jdbc.mysql.parse.select.AggregationsWrapperParse;
import com.yulinlin.jdbc.mysql.parse.select.AsFieldListParse;
import com.yulinlin.jdbc.mysql.parse.select.AsFieldParse;
import com.yulinlin.jdbc.mysql.parse.select.MetricsWrapperParse;
import com.yulinlin.jdbc.mysql.parse.wrapper.InsertFieldsParse;
import com.yulinlin.jdbc.mysql.parse.wrapper.UpdateFieldsParse;

public class MysqlParseManager extends SimpParseManager {



    @Override
    protected void init() {


        this.register(new AsFieldListParse());

        this.register(new AsFieldParse());
        this.register(new MetricsWrapperParse());
        this.register(new AggregationsWrapperParse());
        this.register(new BucketParse());
        this.register(new DateParse());
        this.register(new IntervalParse());



        this.register(new InsertFieldsParse());
        this.register(new UpdateFieldsParse());


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
        this.register(new ExpressionParse());
        this.register(new IsNullParse());
        this.register(new NotParse());
        this.register(new AndParse());
        this.register(new OrParse());

        this.register(new StoreParse());
        this.register(new OrderParse());
        this.register(new JoinParse());




        this.register(new NilParse());


        this.register(new AvgFieldParse());
        this.register(new MaxFieldParse());
        this.register(new MinFieldParse());
        this.register(new SumFieldParse());
        this.register(new CountFieldParse());
        this.register(new DistinctCountParse());


        this.register(new MysqlInsertWrapperParse());
        this.register(new MysqlUpdateWrapperParse());
        this.register(new MysqlDeleteWrapperParse());

        this.register(new MysqlCountWrapperParse());
        this.register(new ExpressionNodeParse());
        this.register(new MysqlSelectWrapperParse());
        this.register(new MysqlGroupWrapperParse());

        this.register(new MysqlConditionWrapperParse());

    }



}
