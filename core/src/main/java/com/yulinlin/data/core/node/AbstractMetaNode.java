package com.yulinlin.data.core.node;


import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMetaNode implements INode {



    private Map<String,Object> meta ;



    public void put(String name, Object value){
        if(meta == null){
            meta = new HashMap<>();
        }
        meta.put(name,value);
    }
    public void put(Map<String,Object> map){

        meta = map;
    }

    public <E> E get(String name){
        return get(name,null);
    }
    public <E> E get(String name,E def){
        if(meta == null){
            return def;
        }
        E val  = (E)meta.computeIfAbsent(name,k -> def);
        return val;
    }
    public boolean containsKey(String name){
        return meta.containsKey(name);
    }

    public Map<String, Object> getMeta() {
        return meta;
    }


}
