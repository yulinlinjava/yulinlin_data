package com.yulinlin.data.core.coder;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class MapDataBuffer extends AbstractDataBuffer {

    private Map<String,Object> data ;

    public MapDataBuffer(boolean encoder, ICoderManager coderManager) {
        super(encoder, coderManager);
        this.data  = new HashMap<>();
    }

    public MapDataBuffer(boolean encoder, ICoderManager coderManager, Map<String, Object> data) {
        super(encoder, coderManager);
        this.data = data;
    }

    @Override
    public Set<String> keys() {
        return data.keySet();
    }

    @Override
    public Map<String, Object> toMap() {

        return data;
    }


    @Override
    public IDataBuffer putAll(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            put(entry.getKey(),entry.getValue());
        }
        return this;
    }

    @Override
    protected void executeEncoderPut(String key, Object value) {
        data.put(key,value);
    }

    @Override
    public <E> E getObject(String key) {
        return (E)data.get(key);
    }



    @Override
    public IDataBuffer copy() {
        MapDataBuffer buffer = new MapDataBuffer(isEncoder(),getCoderManager());
        buffer.data.putAll(data);
        return buffer;
    }

    @Override
    public void clear() {
        data.clear();
    }
}
