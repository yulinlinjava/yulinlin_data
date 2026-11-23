package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Map;

public class UpdateFieldsWrapper<E> extends AbstractUpdateFieldsWrapper<E>

{
    public UpdateFieldsWrapper() {
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public UpdateFieldsWrapper<E> inc(String name, Number value) { super.inc(name, value); return this; }
    @Override public UpdateFieldsWrapper<E> inc(LambdaPropertyFunction<E> name, Number value) { super.inc(name, value); return this; }
    @Override public UpdateFieldsWrapper<E> dec(String name, Number value) { super.dec(name, value); return this; }
    @Override public UpdateFieldsWrapper<E> dec(LambdaPropertyFunction<E> name, Number value) { super.dec(name, value); return this; }
    @Override public UpdateFieldsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public UpdateFieldsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public UpdateFieldsWrapper<E> field(String name, Object value) { super.field(name, value); return this; }
    @Override public UpdateFieldsWrapper<E> field(LambdaPropertyFunction<E> name, Object value) { super.field(name, value); return this; }
    @Override public UpdateFieldsWrapper<E> put(Object value, AtomicValue atomicValue) { super.put(value, atomicValue); return this; }
}
