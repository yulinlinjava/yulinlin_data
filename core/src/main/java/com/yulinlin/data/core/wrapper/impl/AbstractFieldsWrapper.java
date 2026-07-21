package com.yulinlin.data.core.wrapper.impl;

import com.yulinlin.data.core.node.atomic.AtomicValue;
import com.yulinlin.data.core.wrapper.IChildrenWrapper;
import com.yulinlin.data.core.wrapper.IFieldsWrapper;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractFieldsWrapper<E,R extends AbstractFieldsWrapper<E,R>>

        extends IChildrenWrapper<R>
        implements IFieldsWrapper<E,R> {


    private Map<String,AtomicValue> fields = new LinkedHashMap<>();

    private Map<String,Object> meta = new HashMap<>();

    public AbstractFieldsWrapper() {
    }

    public AbstractFieldsWrapper(String name) {
        super(name);
    }


    @Override
    public R field(String name, Object value) {

        return put(value, AtomicValue.instance(name,value));
    }



    public R put( Object value,AtomicValue atomicValue) {
        if(value == null){
            return (R)this;
        }
        atomicValue.put(getMetaAndReset());
        fields.put(atomicValue.getKey(),atomicValue);
        return (R)this;
    }

    @Override
    public R field(LambdaPropertyFunction<E> name, Object value) {
         put(value, AtomicValue.instance(name,value));
        return (R)this;
    }



    public Collection<AtomicValue> getList(){
        return fields.values();
    }

}
