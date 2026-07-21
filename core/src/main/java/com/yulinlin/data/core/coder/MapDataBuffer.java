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
    /*    HashMap<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : this.data.entrySet()) {
            Object value = entry.getValue();
            if(value instanceof IDataBuffer){
                IDataBuffer d = (IDataBuffer)value;
                map.put(entry.getKey(),d.toMap());
            }else if(value instanceof Collection){
              Object val =  ((Collection)value).toArray(new Object[1])[0];
              if(val instanceof IDataBuffer){
                  Collection<IDataBuffer> coll = (Collection)value;
                  List<Map<String, Object>> collect = coll.stream().map(row -> row.toMap()).collect(Collectors.toList());

                  map.put(entry.getKey(),collect);
              }else {
                  map.put(entry.getKey(),entry.getValue());
              }

            }else {
                map.put(entry.getKey(),entry.getValue());
            }


        }
*/
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
