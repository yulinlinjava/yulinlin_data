package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Join;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IGroupWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Map;
import java.util.function.Consumer;

public class GroupWrapper<E> extends AbstractGroupWrapper<E>

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
    public GroupWrapper<E> join(Consumer<IGroupWrapper<E>> func) {
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

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public GroupWrapper<E> having(IConditionWrapper<E> wrapper) { super.having(wrapper); return this; }
    @Override public GroupWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
    @Override public GroupWrapper<E> having(Consumer<IConditionWrapper<E>> func) { super.having(func); return this; }
    @Override public GroupWrapper<E> table(String name) { super.table(name); return this; }
    @Override public GroupWrapper<E> table(String name, String alias) { super.table(name, alias); return this; }
    @Override public GroupWrapper<E> where(IConditionWrapper<E> wrapper) { super.where(wrapper); return this; }
    @Override public GroupWrapper<E> orderBy(String name, boolean asc) { super.orderBy(name, asc); return this; }
    @Override public GroupWrapper<E> page(int pageNumber, int pageSize) { super.page(pageNumber, pageSize); return this; }
    @Override public GroupWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) { super.orderBy(name, asc); return this; }
    @Override public GroupWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) { super.orderByDesc(name); return this; }
    @Override public GroupWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) { super.orderByAsc(name); return this; }
    @Override public GroupWrapper<E> clone() { return (GroupWrapper<E>) super.clone(); }
    @Override public GroupWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public GroupWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public GroupWrapper<E> orderByAsc(String name) { super.orderByAsc(name); return this; }
    @Override public GroupWrapper<E> orderByDesc(String name) { super.orderByDesc(name); return this; }
}
