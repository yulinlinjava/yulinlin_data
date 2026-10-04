package com.yulinlin.elasticsearch.parse;

import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.parse.SimpParseManager;
import com.yulinlin.elasticsearch.parse.base.*;
import com.yulinlin.elasticsearch.parse.from.StoreParse;
import com.yulinlin.elasticsearch.parse.group.BucketParse;
import com.yulinlin.elasticsearch.parse.group.DateParse;
import com.yulinlin.elasticsearch.parse.predicate.AndParse;
import com.yulinlin.elasticsearch.parse.predicate.OrParse;
import com.yulinlin.elasticsearch.parse.script.*;
import com.yulinlin.elasticsearch.parse.wrapper.*;

public class ElasticSearchParseManager extends SimpParseManager  {



    protected void init(){

      //this.register(new CommandNodeParse());

        this.register(new EsInsertFieldsParse());
        this.register(new EsUpdateFieldsParse());


        this.register(new EsConditionWrapperParse());
        this.register(new NestedParse());
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
        this.register(new DistinctCountParse());

        this.register(new BucketParse());
        this.register(new DateParse());


        this.register(new NilParse());

        this.register(new EsGroupWrapperParse());
        this.register(new EsDeleteWrapperParse());
        this.register(new EsInsertWrapperParse());

        this.register(new EsSelectWrapperParse());

        this.register(new EsUpdateWrapperParse());

    }

}
