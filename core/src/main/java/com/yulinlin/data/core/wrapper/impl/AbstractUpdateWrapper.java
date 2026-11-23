package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IUpdateFieldsWrapper;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractUpdateWrapper<E> extends AbstractWrapper<IUpdateWrapper<E>> implements IUpdateWrapper<E> {

    private Store table;

    private IUpdateFieldsWrapper<E> columns;

    private IConditionWrapper<E> where;

    public AbstractUpdateWrapper() {
        this.where = buildWhere();
        this.columns = buildColumns();
    }

    protected abstract IConditionWrapper<E> buildWhere();

    protected abstract IUpdateFieldsWrapper<E> buildColumns();

    @Override
    public AbstractUpdateWrapper<E> table(String name, String alias) {
        return table(name);
    }

    public AbstractUpdateWrapper<E> table(String name){
        this.table = new Store(name);
        return this;
    }

    @Override
    public IUpdateFieldsWrapper<E> fields() {
        return columns;
    }

    @Override
    public AbstractUpdateWrapper<E> fields(Consumer<IUpdateFieldsWrapper<E>> func) {
        func.accept(columns);
        return this;
    }

    @Override
    public IConditionWrapper<E> where() {
        return where;
    }

    @Override
    public AbstractUpdateWrapper<E> where(IConditionWrapper<E> wrapper) {
        wrapper.and(wrapper);
        return this;
    }

    @Override
    public AbstractUpdateWrapper<E> where(Consumer<IConditionWrapper<E>> func) {
        func.accept(where);
        return this;
    }

    public Store getFrom() {
        return table;
    }

    @Override
    public AbstractUpdateWrapper<E> clone()  {
        try {
            return (AbstractUpdateWrapper<E>)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractUpdateWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractUpdateWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}
