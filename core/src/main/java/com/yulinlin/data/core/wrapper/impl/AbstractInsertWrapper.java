package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.IInsertFieldsWrapper;
import com.yulinlin.data.core.wrapper.IInsertWrapper;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractInsertWrapper<E> extends AbstractWrapper<IInsertWrapper<E>> implements IInsertWrapper<E> {

    private Store table;

    private IInsertFieldsWrapper<E> columns;

    public AbstractInsertWrapper() {
        this.columns = buildColumns();
    }

    protected abstract IInsertFieldsWrapper<E> buildColumns();

    public AbstractInsertWrapper<E> table(String name){
        this.table = new Store(name);
        return this;
    }

    @Override
    public IInsertFieldsWrapper<E> fields() {
        return columns;
    }

    @Override
    public AbstractInsertWrapper<E> fields(Consumer<IInsertFieldsWrapper<E>> func) {
        func.accept(columns);
        return this;
    }

    @Override
    public AbstractInsertWrapper<E> table(String name, String alias) {
        return table(name);
    }

    public Store getFrom() {
        return table;
    }

    @Override
    public AbstractInsertWrapper<E> clone()  {
        try {
            return (AbstractInsertWrapper)super.clone();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractInsertWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractInsertWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}
