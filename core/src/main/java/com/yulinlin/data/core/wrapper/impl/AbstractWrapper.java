package com.yulinlin.data.core.wrapper.impl;


import com.yulinlin.data.core.node.IMetaNode;

import java.util.HashMap;
import java.util.Map;


public abstract class AbstractWrapper<R> implements IMetaNode<R> {

    private Map<String,Object> meta = new HashMap<>();

    protected Map<String,Object> getMetaAndReset(){
        Map map = meta;
        meta = new HashMap<>();
        return map;
    }


    public R meta(String name, Object value) {
        meta.put(name,value);
        return (R)this;
    }

    @Override
    public R meta(Map<String, Object> map) {
        meta.putAll(map);
        return (R)this;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }



}
