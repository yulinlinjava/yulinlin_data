package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.metrics.*;
import com.yulinlin.data.core.node.select.FunctionAsField;
import com.yulinlin.data.core.wrapper.IChildrenWrapper;
import com.yulinlin.data.core.wrapper.IMetricsWrapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMetricsWrapper<E, R extends AbstractMetricsWrapper<E,R>

        > extends IChildrenWrapper<R> implements IMetricsWrapper<E,R> {

    private Map<String,FunctionAsField> map = new HashMap<>();


    public AbstractMetricsWrapper() {
    }

    public AbstractMetricsWrapper(String name) {
        super(name);
    }

    private R addNode(AbstractMetrics node, String alias){
        node.put(getMetaAndReset());
        FunctionAsField asField = new FunctionAsField(node,alias);
        map.put(node.getKey(),asField);

        return (R)this;
    }



    @Override
    public R distinctCount(String name, String alias) {
        DistinctCountMetrics distinctItem = new DistinctCountMetrics(name);
        return addNode(distinctItem,alias);
    }


    @Override
    public R count(String name, String alias) {
        CountMetrics distinctItem = new CountMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public R sum(String name, String alias) {
        SumMetrics distinctItem = new SumMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public R avg(String name, String alias) {
        AvgMetrics distinctItem = new AvgMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public R min(String name, String alias) {
        MinMetrics distinctItem = new MinMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public R max(String name, String alias) {
        MaxMetrics distinctItem = new MaxMetrics(name);
        return addNode(distinctItem,alias);
    }


    public Collection<FunctionAsField> getList() {
        return map.values();
    }


}
