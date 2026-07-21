package com.yulinlin.data.core.coder.impl;

import com.yulinlin.data.lang.util.CodeObject;
import com.yulinlin.data.lang.util.Currency;
import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;

public class CodeObjectCoder implements ICoder<CodeObject,Object> {


    @Override
    public Object encode(IDataBuffer buffer, String key, CodeObject value) {

        return value.encode();

    }

    @Override
    public CodeObject decode(IDataBuffer buffer , Field field, Object value) {
        CodeObject currency = (CodeObject)ReflectionUtil.newInstance(field.getType());
        currency.decode(value);
        return currency;
    }


    @Override
    public int priority() {
        return 2;
    }

    @Override
    public boolean check(Class clazz) {
        return Currency.class.isAssignableFrom(clazz);
    }
}
