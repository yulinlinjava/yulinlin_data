package com.yulinlin.jdbc.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractMapCoder;
import com.yulinlin.data.lang.json.JsonUtil;

import java.util.Map;


class MapCoder extends AbstractMapCoder<String> {


    @Override
    public String encode(IDataBuffer buffer, String key, Map value) {


        return JsonUtil.toJson(value);
    }

    @Override
    public Class getTypeClass() {
        return Map.class;
    }
}
