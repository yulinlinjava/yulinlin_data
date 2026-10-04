package com.yulinlin.data.core.node.predicate;

import com.yulinlin.data.core.node.AbstractMetaNode;
import com.yulinlin.data.core.node.ICondition;

import java.util.ArrayList;
import java.util.List;

public abstract class Predicates extends AbstractMetaNode implements ICondition {


    private List<ICondition> list;

    public Predicates() {
        this.list = new ArrayList<>();
    }


    public Predicates(List<ICondition> list) {
        this.list = list;
    }

    public void add(ICondition condition) {
        list.add(condition);
    }
    public boolean isEmpty(){
        return list.isEmpty();
    }


    public List<ICondition> getList() {
        return list;
    }
}
