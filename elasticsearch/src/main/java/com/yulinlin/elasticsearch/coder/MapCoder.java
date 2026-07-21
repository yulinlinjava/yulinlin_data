package com.yulinlin.elasticsearch.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AbstractMapCoder;

import java.util.Map;


class MapCoder extends AbstractMapCoder<Map> {


    @Override
    public Map encode(IDataBuffer buffer, String key, Map value) {


        return value;
    }

    @Override
    public Class getTypeClass() {
        return Map.class;
    }
}
