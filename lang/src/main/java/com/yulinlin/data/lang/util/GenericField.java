package com.yulinlin.data.lang.util;


import java.lang.reflect.Type;


public class GenericField extends BaseGeneric{

    public GenericField(BaseGeneric prev, Type type) {
        super(prev, type);
    }

    public GenericField(Type type) {
        super(type);
    }
}
