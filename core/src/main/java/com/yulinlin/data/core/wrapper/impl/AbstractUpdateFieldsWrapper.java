package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.wrapper.IUpdateFieldsWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Map;

public abstract class AbstractUpdateFieldsWrapper<E>

            extends AbstractFieldsWrapper<E>

        implements IUpdateFieldsWrapper<E>

{

    public AbstractUpdateFieldsWrapper() {
    }

    @Override
    public AbstractUpdateFieldsWrapper<E> inc(String name, Number value) {
        AtomicValue atomicValue = AtomicValue.incInstance(name, value);

        put(value, atomicValue);
        return this;
    }

    @Override
    public AbstractUpdateFieldsWrapper<E> inc(LambdaPropertyFunction<E> name, Number value) {
        AtomicValue atomicValue = AtomicValue.incInstance(name, value);

        put(value, atomicValue);
        return this;
    }

    @Override
    public AbstractUpdateFieldsWrapper<E> dec(String name, Number value) {
        AtomicValue atomicValue = AtomicValue.decInstance(name, value);

        put(value, atomicValue);
        return this;
    }

    @Override
    public AbstractUpdateFieldsWrapper<E> dec(LambdaPropertyFunction<E> name, Number value) {
        AtomicValue atomicValue = AtomicValue.decInstance(name, value);

        put(value, atomicValue);
        return this;
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractUpdateFieldsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractUpdateFieldsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public AbstractUpdateFieldsWrapper<E> field(String name, Object value) { super.field(name, value); return this; }
    @Override public AbstractUpdateFieldsWrapper<E> field(LambdaPropertyFunction<E> name, Object value) { super.field(name, value); return this; }
    @Override public AbstractUpdateFieldsWrapper<E> put(Object value, AtomicValue atomicValue) { super.put(value, atomicValue); return this; }
}
