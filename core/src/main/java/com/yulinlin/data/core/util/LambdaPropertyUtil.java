package com.yulinlin.data.core.util;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.lambda.LambdaUtils;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.util.StringUtil;

import java.lang.reflect.Field;

public class LambdaPropertyUtil {

    public static String getColumnName(LambdaPropertyFunction function){
        return getColumnName(function,true);
    }
    public static String getColumnName(LambdaPropertyFunction function,boolean map){
        Field field = LambdaUtils.lambdaMethodNameToField(function);
        String name = field.getName();
        JoinField annotation = AnnotationUtil.findAnnotation(field, JoinField.class);
        if(annotation != null && annotation.name().length() > 0){
            name =  annotation.name();
        }
        if(map){
            return StringUtil.javaToColumn(name);
        }
        return name;
    }

}
