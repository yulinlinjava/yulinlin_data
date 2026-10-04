package com.yulinlin.data.core.util;

import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.KeyEnum;
import com.yulinlin.data.core.anno.MetaParam;

import java.util.HashMap;
import java.util.Map;

public class MetaUtil {

    public static Map<String,Object> toMap(JoinMeta meta){
        if(meta == null){
            return new HashMap<>();
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put(KeyEnum.primaryKey,meta.primaryKey());
        map.put(KeyEnum.rw,meta.rw());

        for (MetaParam param : meta.params()) {
            map.put(param.name(),param.value());
        }
        return map;
    }
}
