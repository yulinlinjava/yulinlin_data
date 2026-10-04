package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Join;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IJoinWrapper;

import java.util.function.Consumer;

public class SelectWrapper<E> extends AbstractSelectWrapper<E,SelectWrapper<E>,ConditionManager<E>,AsFieldListWrapper<E>>

    {

    public SelectWrapper() {
        super(new ConditionManager<>());
    }

    @Override
    protected AsFieldListWrapper<E> buildSelects() {
        return new AsFieldListWrapper();
    }

    @Override
    public SelectWrapper<E> join(Consumer<SelectWrapper<E>> func) {
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
