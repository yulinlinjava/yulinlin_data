package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Join;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IJoinWrapper;

import java.util.function.Consumer;

public class GroupWrapper<E> extends AbstractGroupWrapper<
        E,

        GroupWrapper<E>,

        ConditionManager<E>,

        MetricsWrapper<E>,

        AggregationsWrapper<E>

        >



{

    public GroupWrapper() {
    }

    @Override
    protected ConditionManager<E> buildWhere() {
        return new ConditionManager<>();
    }

    @Override
    protected AggregationsWrapper<E> buildAggregations() {
        return new AggregationsWrapper<>();
    }

    @Override
    protected MetricsWrapper<E> buildMetrics() {
        return new MetricsWrapper<>();
    }

    @Override
    public GroupWrapper<E> join(Consumer<GroupWrapper<E>> func) {
        func.accept(this);
        return this;
    }

    @Override
    public ConditionManager<E> left(String name, String alias) {
        ConditionManager manager =   new ConditionManager();

        this.from =  new Join(from,new Store(name,alias),Join.JoinEnum.left,manager);

        return manager;
    }

    @Override
    public ConditionManager<E> right(String name, String alias) {
        ConditionManager manager =   new ConditionManager();

        this.from =  new Join(from,new Store(name,alias),Join.JoinEnum.right,manager);

        return manager;
    }

    @Override
    public ConditionManager<E> inner(String name, String alias) {
        ConditionManager manager =   new ConditionManager();

        this.from =  new Join(from,new Store(name,alias),Join.JoinEnum.inner,manager);

        return manager;
    }
}
