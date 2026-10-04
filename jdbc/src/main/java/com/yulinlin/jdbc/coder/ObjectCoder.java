
package com.yulinlin.jdbc.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractObjectCoder;
import com.yulinlin.data.lang.json.JsonUtil;

class ObjectCoder extends AbstractObjectCoder<String> {


     @Override
     public String encode(IDataBuffer buffer, String key, Object value) {

         return JsonUtil.toJson(value);

     }

    @Override
    public Object decode(IDataBuffer buffer, Class clazz, Object value) {
        return super.decode(buffer, clazz, value);
    }
}

