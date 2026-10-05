package com.yulinlin.jdbc.sql;


import com.yulinlin.data.core.parse.SimpParseManager;
import com.yulinlin.jdbc.sql.parse.ExpressionNodeParse;
import com.yulinlin.jdbc.sql.parse.base.*;
import com.yulinlin.jdbc.sql.parse.from.JoinParse;
import com.yulinlin.jdbc.sql.parse.from.StoreParse;
import com.yulinlin.jdbc.sql.parse.group.BucketParse;
import com.yulinlin.jdbc.sql.parse.statement.*;
import com.yulinlin.jdbc.sql.parse.order.OrderParse;
import com.yulinlin.jdbc.sql.parse.predicate.AndParse;
import com.yulinlin.jdbc.sql.parse.predicate.NotParse;
import com.yulinlin.jdbc.sql.parse.predicate.OrParse;
import com.yulinlin.jdbc.sql.parse.script.*;
import com.yulinlin.jdbc.sql.parse.select.AggregationsWrapperParse;
import com.yulinlin.jdbc.sql.parse.select.AsFieldListParse;
import com.yulinlin.jdbc.sql.parse.select.AsFieldParse;
import com.yulinlin.jdbc.sql.parse.select.MetricsWrapperParse;
import com.yulinlin.jdbc.sql.parse.wrapper.InsertFieldsParse;
import com.yulinlin.jdbc.sql.parse.wrapper.UpdateFieldsParse;

public class SqlParseManager extends SimpParseManager {

    public com.yulinlin.jdbc.sql.parse.NameParse nameParse() {
        return (com.yulinlin.jdbc.sql.parse.NameParse) parseMap.get(com.yulinlin.data.core.node.AbstractMetaNode.class);
    }

    @Override
    public Object parse(com.yulinlin.data.core.node.INode node,
                        com.yulinlin.data.core.parse.IParamsContext params) {
        if (node == null) return null;
        // Wrap once per root parse; recursive calls reuse the same request-local state.
        if (!(params instanceof SqlParamsContext sql) || sql.parseManager() != this) {
            params = new SqlParamsContext(params, this);
        }
        return super.parse(node, params);
    }



    @Override
    protected void init() {


        this.register(new AsFieldListParse());

        this.register(new AsFieldParse());
        this.register(new MetricsWrapperParse());
        this.register(new AggregationsWrapperParse());
        this.register(new BucketParse());
        this.register(new com.yulinlin.jdbc.sql.parse.NameParse());
        this.register(new PageParse());





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
        this.register(new NestedParse());


        this.register(new AvgFieldParse());
        this.register(new MaxFieldParse());
        this.register(new MinFieldParse());
        this.register(new SumFieldParse());
        this.register(new CountFieldParse());
        this.register(new DistinctCountParse());


        this.register(new SqlInsertWrapperParse());
        this.register(new SqlUpdateWrapperParse());
        this.register(new SqlDeleteWrapperParse());

        this.register(new SqlCountWrapperParse());
        this.register(new ExpressionNodeParse());
        this.register(new SqlSelectWrapperParse());
        this.register(new SqlGroupWrapperParse());

        this.register(new SqlConditionWrapperParse());

    }



}
