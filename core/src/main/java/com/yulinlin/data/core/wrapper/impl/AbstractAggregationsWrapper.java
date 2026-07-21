package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.IGroupNode;
import com.yulinlin.data.core.node.group.BucketGroup;
import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.node.select.GroupAsField;
import com.yulinlin.data.core.wrapper.IAggregationsWrapper;
import com.yulinlin.data.core.wrapper.IChildrenWrapper;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAggregationsWrapper<E,
        R extends AbstractAggregationsWrapper<E,R>
        
        > extends IChildrenWrapper<R> implements IAggregationsWrapper<E,R> {

    private List<GroupAsField> list = new ArrayList<>();

    public AbstractAggregationsWrapper() {
    }

    public AbstractAggregationsWrapper(String name) {
        super(name);
    }

    private R addNode(IGroupNode node, String alias){
        list.add(new GroupAsField(node,alias));
        return (R)this;
    }

    public List<GroupAsField> getList() {
        return list;
    }

    public R field(String  name, String alias){
        return addNode(new BucketGroup(name),alias);
    }
    public R minute(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.minute),alias);
    }

    public R hour(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.hour),alias);
    }



    public R day(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.day),alias);
    }

    public R month(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.month),alias);
    }

    public R quarter(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.quarter),alias);
    }

    public R interval(String  name,  String alias,int interval){
        return addNode(new IntervalGroup(name,interval),alias);
    }


    public R year(String  name, String alias){
        return addNode(new DateGroup(name,DateGroup.Type.year),alias);
    }



}
