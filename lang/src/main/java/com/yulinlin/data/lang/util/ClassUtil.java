package com.yulinlin.data.lang.util;

import java.util.Set;

public class ClassUtil {

 private static final Set<Class<?>> PRIMITIVE_LIKE = Set.of(
         Boolean.class, Byte.class, Character.class, Short.class, Integer.class,
         Long.class, Float.class, Double.class, Void.class, String.class);


    public static boolean isPrimitive(Class clazz){
        if(clazz.isPrimitive()){
            return true;
        }
        return PRIMITIVE_LIKE.contains(clazz);


    }
}
