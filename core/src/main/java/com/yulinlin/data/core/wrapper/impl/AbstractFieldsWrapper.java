package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.wrapper.IFieldsWrapper;
import com.yulinlin.data.core.wrapper.IWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractFieldsWrapper<E>

        extends AbstractWrapper<IFieldsWrapper<E>>
        implements IFieldsWrapper<E>, IWrapper {

    private Map<String,AtomicValue> fields = new LinkedHashMap<>();

    private Map<String,Object> meta = new HashMap<>();

    public AbstractFieldsWrapper() {
    }

    @Override
    public AbstractFieldsWrapper<E> field(String name, Object value) {

        return put(value, AtomicValue.instance(name,value));
    }

    public AbstractFieldsWrapper<E> put( Object value,AtomicValue atomicValue) {
        if(value == null){
            return this;
        }
        atomicValue.put(getMetaAndReset());
        fields.put(atomicValue.getKey(),atomicValue);
        return this;
    }

    @Override
    public AbstractFieldsWrapper<E> field(LambdaPropertyFunction<E> name, Object value) {
         put(value, AtomicValue.instance(name,value));
        return this;
    }

    public Collection<AtomicValue> getList(){
        return fields.values();
    }

    // Preserve the owning type in fluent calls without recursive public generics.
    @Override public AbstractFieldsWrapper<E> meta(String name, Object value) { super.meta(name, value); return this; }
    @Override public AbstractFieldsWrapper<E> meta(Map<String, Object> values) { super.meta(values); return this; }
}
