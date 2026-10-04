package com.yulinlin.data.core.wrapper.impl;


import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IUpdateFieldsWrapper;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;

import java.util.function.Consumer;

public abstract class AbstractUpdateWrapper<E,
        R extends AbstractUpdateWrapper<E,R,W,U>,
        W extends IConditionWrapper<E,W>,
        U  extends IUpdateFieldsWrapper<E,U>
        > extends AbstractWrapper<R> implements IUpdateWrapper<E,R,W,U> {

    private Store table;

    private U columns;

    private W where;

    public AbstractUpdateWrapper() {
        this.where = buildWhere();
        this.columns = buildColumns();
    }

    protected abstract W buildWhere();

    protected abstract U buildColumns();

    @Override
    public R table(String name, String alias) {
        return table(name);
    }

    public R table(String name){
        this.table = new Store(name);
        return (R)this;
    }

    @Override
    public U fields() {
        return columns;
    }

    @Override
    public R fields(Consumer<U> func) {
        func.accept(columns);
        return (R)this;
    }

    /*   @Override
    public <S extends IUpdateFieldsWrapper<E, S>> IUpdateFieldsWrapper<E, S> fields() {
        return columns;
    }

    @Override
    public <S extends IUpdateFieldsWrapper<E, S>> R fields(Consumer<IUpdateFieldsWrapper<E, S>> func) {
        func.accept(columns);
        return (R)this;
    }*/



    @Override
    public W where() {
        return where;
    }

    @Override
    public R where(W wrapper) {
        wrapper.and(wrapper);
        return (R)this;
    }

    @Override
    public R where(Consumer<W> func) {
        func.accept(where);
        return (R)this;
    }

    public Store getFrom() {
        return table;
    }

    @Override
    public R clone()  {
        try {
            return (R)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
