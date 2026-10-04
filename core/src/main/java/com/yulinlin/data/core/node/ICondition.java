package com.yulinlin.data.core.node;

import com.yulinlin.data.core.node.predicate.And;
import com.yulinlin.data.core.node.predicate.Not;
import com.yulinlin.data.core.node.predicate.Or;

public interface ICondition  extends INode{



    default ICondition and(ICondition condition){
        And and;
        if(this instanceof And){
            and = (And)this;
        }else{
            and = new And();
            and.add(this);
        }
        and.add(condition);
        return and;
    }

    default ICondition or(ICondition condition){
        Or and;
        if(this instanceof Or){
            and = (Or)this;
        }else{
            and = new Or();

            and.add(this);
        }
        and.add(condition);
        return and;
    }

    default ICondition not(ICondition condition){
        Not and;
        if(this instanceof Not){
            and = (Not)this;
        }else{
            and = new Not();

            and.add(this);
        }
        and.add(condition);
        return and;
    }



}
