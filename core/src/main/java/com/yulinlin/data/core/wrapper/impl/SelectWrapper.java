package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Join;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IAsFieldListWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.ISelectWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Map;
import java.util.function.Consumer;

public class SelectWrapper<E> extends AbstractSelectWrapper<E>

    {

    public SelectWrapper() {
        super(new ConditionManager<>());
    }

    @Override
    protected AsFieldListWrapper<E> buildSelects() {
        return new AsFieldListWrapper();
    }

    @Override
    public SelectWrapper<E> join(Consumer<ISelectWrapper<E>> func) {
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
    @Override public SelectWrapper<E> table(String name) { super.table(name); return this; }
    @Override public SelectWrapper<E> table(String name, String alias) { super.table(name, alias); return this; }
    @Override public SelectWrapper<E> lock() { super.lock(); return this; }
    @Override public SelectWrapper<E> fields(Consumer<IAsFieldListWrapper<E>> consumer) { super.fields(consumer); return this; }
    @Override public SelectWrapper<E> highlight(String name) { super.highlight(name); return this; }
    @Override public SelectWrapper<E> highlight(LambdaPropertyFunction<E> name) { super.highlight(name); return this; }
    @Override public SelectWrapper<E> orderBy(String name, boolean asc) { super.orderBy(name, asc); return this; }
    @Override public SelectWrapper<E> page(int pageNumber, int pageSize) { super.page(pageNumber, pageSize); return this; }
    @Override public SelectWrapper<E> where(IConditionWrapper<E> wrapper) { super.where(wrapper); return this; }
    @Override public SelectWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
    @Override public SelectWrapper<E> orderBy(LambdaPropertyFunction<E> name, boolean asc) { super.orderBy(name, asc); return this; }
    @Override public SelectWrapper<E> orderByDesc(LambdaPropertyFunction<E> name) { super.orderByDesc(name); return this; }
    @Override public SelectWrapper<E> orderByAsc(LambdaPropertyFunction<E> name) { super.orderByAsc(name); return this; }
    @Override public SelectWrapper<E> clone() { return (SelectWrapper<E>) super.clone(); }
    @Override public SelectWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public SelectWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public SelectWrapper<E> orderByAsc(String name) { super.orderByAsc(name); return this; }
    @Override public SelectWrapper<E> orderByDesc(String name) { super.orderByDesc(name); return this; }
}
