package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.anno.AggregationsEnum;
import com.yulinlin.data.core.node.IGroupNode;
import com.yulinlin.data.core.node.group.BucketGroup;
import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.node.select.GroupAsField;
import com.yulinlin.data.core.wrapper.IAggregationsWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractAggregationsWrapper<E> extends AbstractWrapper<IAggregationsWrapper<E>> implements IAggregationsWrapper<E> {

    private List<GroupAsField> list = new ArrayList<>();

    public AbstractAggregationsWrapper() {
    }

    private AbstractAggregationsWrapper<E> addNode(IGroupNode node, String alias){
        list.add(new GroupAsField(node,alias));
        return this;
    }

    public List<GroupAsField> getList() {
        return list;
    }

    public AbstractAggregationsWrapper<E> field(String  name, String alias){
        return addNode(new BucketGroup(name),alias);
    }
    public AbstractAggregationsWrapper<E> minute(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.minute),alias);
    }

    public AbstractAggregationsWrapper<E> hour(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.hour),alias);
    }

    public AbstractAggregationsWrapper<E> day(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.day),alias);
    }

    public AbstractAggregationsWrapper<E> month(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.month),alias);
    }

    public AbstractAggregationsWrapper<E> quarter(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.quarter),alias);
    }

    public AbstractAggregationsWrapper<E> interval(String  name,  String alias,int interval){
        return addNode(new IntervalGroup(name,interval),alias);
    }

    public AbstractAggregationsWrapper<E> year(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.year),alias);
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractAggregationsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractAggregationsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public AbstractAggregationsWrapper<E> field(AggregationsEnum type, String name, String alias) { IAggregationsWrapper.super.field(type, name, alias); return this; }
}
