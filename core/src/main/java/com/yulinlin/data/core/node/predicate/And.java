package com.yulinlin.data.core.node.predicate;

import com.yulinlin.data.core.node.ICondition;

import java.util.List;

public class And extends Predicates implements ICondition {


    public And() {
    }

    public And(List<ICondition> list) {
        super(list);
    }
}
