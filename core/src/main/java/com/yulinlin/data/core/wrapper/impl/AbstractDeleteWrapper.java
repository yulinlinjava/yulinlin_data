package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IDeleteWrapper;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractDeleteWrapper<E>

        extends AbstractWrapper<IDeleteWrapper<E>> implements IDeleteWrapper<E> {

    private Store from;

    private IConditionWrapper<E> where;

    public AbstractDeleteWrapper() {
        this.where = buildWhere();
    }

    public AbstractDeleteWrapper<E> table(String name){
        this.from = new Store(name);
        return this;
    }

    protected abstract IConditionWrapper<E> buildWhere();

   @Override
    public AbstractDeleteWrapper<E> table(String name, String alias) {
        return table(name);
    }

    public IConditionWrapper<E> where(){
        return where;
    }

    @Override
    public AbstractDeleteWrapper<E> where(IConditionWrapper<E> wrapper) {
        where.and(wrapper);
        return this;
    }

    @Override
    public AbstractDeleteWrapper<E> where(Consumer<IConditionWrapper<E>> func) {
        func.accept(where);
        return this;
    }

    public Store getFrom() {
        return from;
    }

    @Override
    public AbstractDeleteWrapper<E> clone()  {
        try {
            return (AbstractDeleteWrapper<E>)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractDeleteWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractDeleteWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}
