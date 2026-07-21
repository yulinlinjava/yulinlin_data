package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.wrapper.IUpdateFieldsWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

public abstract class AbstractUpdateFieldsWrapper<E,R extends AbstractUpdateFieldsWrapper<E,R>>

            extends AbstractFieldsWrapper<E,R>

        implements IUpdateFieldsWrapper<E,R>

{

    public AbstractUpdateFieldsWrapper() {
    }

    public AbstractUpdateFieldsWrapper(String name) {
        super(name);
    }


    @Override
    public R inc(String name, Number value) {
        AtomicValue atomicValue = AtomicValue.incInstance(name, value);

        return put(value, atomicValue);
    }

    @Override
    public R inc(LambdaPropertyFunction<E> name, Number value) {
        AtomicValue atomicValue = AtomicValue.incInstance(name, value);

        return put(value, atomicValue);
    }

    @Override
    public R dec(String name, Number value) {
        AtomicValue atomicValue = AtomicValue.decInstance(name, value);

        return put(value, atomicValue);
    }

    @Override
    public R dec(LambdaPropertyFunction<E> name, Number value) {
        AtomicValue atomicValue = AtomicValue.decInstance(name, value);

        return put(value, atomicValue);
    }




}
