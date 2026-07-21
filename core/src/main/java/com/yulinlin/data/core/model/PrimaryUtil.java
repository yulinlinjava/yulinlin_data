package com.yulinlin.data.core.model;

import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.exception.CodeException;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PrimaryUtil {

    static  Map<Class,String> cache = new ConcurrentHashMap<>();


    public static String getPrimaryKeyName(Class clazz){

        return cache.computeIfAbsent(clazz,(k) -> {
            List<Field> fs =  ReflectionUtil.getAllDeclaredFields(clazz);
            for (Field f : fs) {

                JoinMeta meta =              AnnotationUtils.findAnnotation(f,JoinMeta.class);
                if(meta != null && meta.primaryKey()){
                    return f.getName();
                }
            }
            throw new CodeException("缺少主键");
        });


    }

    public static Object getPrimaryKeyValue(Object obj){
        String primaryKeyName = getPrimaryKeyName(obj.getClass());
        return ReflectionUtil.invokeGetter(obj,primaryKeyName);
    }

    public static <E> E copyByPrimaryKey(E obj){
        String primaryKeyName = getPrimaryKeyName(obj.getClass());
        Object id =  ReflectionUtil.invokeGetter(obj,primaryKeyName);
        Object bean = ReflectionUtil.newInstance(obj.getClass());
        ReflectionUtil.invokeSetter(bean,primaryKeyName,id);
        return (E)bean;
    }

}
