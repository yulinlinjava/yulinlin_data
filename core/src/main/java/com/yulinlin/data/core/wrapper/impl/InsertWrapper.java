package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.wrapper.IInsertFieldsWrapper;
import java.util.Map;
import java.util.function.Consumer;

public final class InsertWrapper<E> extends AbstractInsertWrapper<E> {

    public InsertWrapper() {
    }

    @Override
    protected InsertFieldsWrapper<E> buildColumns() {
        return new InsertFieldsWrapper();
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public InsertWrapper<E> table(String name) { super.table(name); return this; }
    @Override public InsertWrapper<E> fields(Consumer<IInsertFieldsWrapper<E>> func) { super.fields(func); return this; }
    @Override public InsertWrapper<E> table(String name, String alias) { super.table(name, alias); return this; }
    @Override public InsertWrapper<E> clone() { return (InsertWrapper<E>) super.clone(); }
    @Override public InsertWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public InsertWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}
