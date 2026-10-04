package com.yulinlin.mongodb.parse;

import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.node.AbstractMetaNode;
import com.yulinlin.data.core.node.MetaNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.mongodb.enums.MongoKeys;

public class AliasUtil {

    private static ThreadLocal<Boolean> local = ThreadLocal.withInitial(() -> true);


    public static void set(boolean open){
        local.set(open);
    }

    public static String parse(String name, IParamsContext params) {
        if(local.get()){
            return
                    params.toColumn(
                         name
                    );
        }else {
            return  name;

        }

    }
    public static String parse(AbstractMetaNode node, IParamsContext params) {

        String name = (String) node.get(MongoKeys.name);
        if(name != null){
            return name;
        }

        if(node instanceof MetaNode){
            MetaNode m =(MetaNode) node;
            name  = m.getKey();

            return parse(name,params);
        }else {
            throw new NoticeException("解析key失败");
        }

    }

}
