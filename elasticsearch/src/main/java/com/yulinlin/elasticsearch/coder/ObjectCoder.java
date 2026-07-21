
package com.yulinlin.elasticsearch.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractObjectCoder;

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

