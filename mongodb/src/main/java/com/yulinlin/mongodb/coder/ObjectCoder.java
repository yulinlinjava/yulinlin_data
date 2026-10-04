
package com.yulinlin.mongodb.coder;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractObjectCoder;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.Map;

class ObjectCoder extends AbstractObjectCoder<Object> {



    @Override
    public Object encode(IDataBuffer buffer, String key, Object value) {
        return value;
    }

    @Override
    public boolean check(Class clazz) {
        return true;
    }

     @Override
    public int priority() {
        return 3;
    }
}

