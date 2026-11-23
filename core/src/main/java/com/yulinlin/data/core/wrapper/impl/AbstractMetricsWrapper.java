package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.anno.MetricsEnum;
import com.yulinlin.data.core.node.metrics.*;
import com.yulinlin.data.core.node.select.FunctionAsField;
import com.yulinlin.data.core.wrapper.IMetricsWrapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMetricsWrapper<E> extends AbstractWrapper<IMetricsWrapper<E>> implements IMetricsWrapper<E> {

    private Map<String,FunctionAsField> map = new HashMap<>();

    public AbstractMetricsWrapper() {
    }

    private AbstractMetricsWrapper<E> addNode(AbstractMetrics node, String alias){
        node.put(getMetaAndReset());
        FunctionAsField asField = new FunctionAsField(node,alias);
        map.put(node.getKey(),asField);

        return this;
    }

    @Override
    public AbstractMetricsWrapper<E> distinctCount(String name, String alias) {
        DistinctCountMetrics distinctItem = new DistinctCountMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public AbstractMetricsWrapper<E> count(String name, String alias) {
        CountMetrics distinctItem = new CountMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public AbstractMetricsWrapper<E> sum(String name, String alias) {
        SumMetrics distinctItem = new SumMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public AbstractMetricsWrapper<E> avg(String name, String alias) {
        AvgMetrics distinctItem = new AvgMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public AbstractMetricsWrapper<E> min(String name, String alias) {
        MinMetrics distinctItem = new MinMetrics(name);
        return addNode(distinctItem,alias);
    }

    @Override
    public AbstractMetricsWrapper<E> max(String name, String alias) {
        MaxMetrics distinctItem = new MaxMetrics(name);
        return addNode(distinctItem,alias);
    }

    public Collection<FunctionAsField> getList() {
        return map.values();
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractMetricsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractMetricsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public AbstractMetricsWrapper<E> field(MetricsEnum type, String name, String alias) { IMetricsWrapper.super.field(type, name, alias); return this; }
}
