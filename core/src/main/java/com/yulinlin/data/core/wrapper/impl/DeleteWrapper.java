package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.wrapper.IConditionWrapper;
import java.util.Map;
import java.util.function.Consumer;

public final class DeleteWrapper<E> extends AbstractDeleteWrapper<E> {

    public DeleteWrapper() {
    }

    @Override
    protected ConditionManager<E> buildWhere() {
        return new ConditionManager<>();
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public DeleteWrapper<E> table(String name) { super.table(name); return this; }
    @Override public DeleteWrapper<E> table(String name, String alias) { super.table(name, alias); return this; }
    @Override public DeleteWrapper<E> where(IConditionWrapper<E> wrapper) { super.where(wrapper); return this; }
    @Override public DeleteWrapper<E> where(Consumer<IConditionWrapper<E>> func) { super.where(func); return this; }
    @Override public DeleteWrapper<E> clone() { return (DeleteWrapper<E>) super.clone(); }
    @Override public DeleteWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public DeleteWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}
