package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Map;

public class InsertFieldsWrapper<E>
        extends AbstractInsertFieldsWrapper<E>

{

    public InsertFieldsWrapper() {
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public InsertFieldsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public InsertFieldsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
    @Override public InsertFieldsWrapper<E> field(String name, Object value) { super.field(name, value); return this; }
    @Override public InsertFieldsWrapper<E> field(LambdaPropertyFunction<E> name, Object value) { super.field(name, value); return this; }
    @Override public InsertFieldsWrapper<E> put(Object value, AtomicValue atomicValue) { super.put(value, atomicValue); return this; }
}
