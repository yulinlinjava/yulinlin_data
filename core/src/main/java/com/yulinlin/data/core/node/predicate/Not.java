package com.yulinlin.data.core.node.predicate;

import com.yulinlin.data.core.node.ICondition;

import java.util.List;

public class Not extends Predicates {

    public Not() {
    }

    public Not(List<ICondition> list) {
        super(list);
    }
}
