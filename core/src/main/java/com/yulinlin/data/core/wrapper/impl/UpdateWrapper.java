package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IUpdateFieldsWrapper;
import java.util.Map;
import java.util.function.Consumer;

public final class UpdateWrapper<E> extends AbstractUpdateWrapper<E> {

    public UpdateWrapper() {
    }

    @Override
    protected ConditionManager<E> buildWhere() {
        return new ConditionManager();
    }

    @Override
    protected UpdateFieldsWrapper<E> buildColumns() {
        return new UpdateFieldsWrapper<>();
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public UpdateWrapper<E> table(String name, String alias) { super.table(name, alias); return this; }
    @Override public UpdateWrapper<E> table(String name) { super.table(name); return this; }
    @Override public UpdateWrapper<E> fields(Consumer<IUpdateFieldsWrapper<E>> func) { super.fields(func); return this; }
    @Override public UpdateWrapper<E> where(IConditionWrapper<E> wrapper) { super.where(wrapper); return this; }
    @Override public UpdateWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
    @Override public UpdateWrapper<E> clone() { return (UpdateWrapper<E>) super.clone(); }
    @Override public UpdateWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public UpdateWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}
