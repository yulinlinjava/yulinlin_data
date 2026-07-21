package com.yulinlin.data.core.node;


import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.lambda.LambdaUtils;

import java.lang.reflect.Field;

public abstract class MetaNode  extends  AbstractMetaNode{

    private String key;


    public MetaNode(Object name) {
        if(name instanceof LambdaPropertyFunction){
            Field field = LambdaUtils.lambdaMethodNameToField(name);
            key =  field.getName();
        }else {
            key = name.toString();
        }
    }


    public String getKey() {
        return key;
    }
}
