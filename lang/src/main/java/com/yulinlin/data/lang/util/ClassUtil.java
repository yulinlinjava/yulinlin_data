package com.yulinlin.data.lang.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ClassUtil {

 static Set classSet =   new HashSet(
        Arrays.asList(Byte.class,Character.class,Integer.class,Long.class,Float.class,Double.class,String.class)
    );;


    public static boolean isPrimitive(Class clazz){
        if(clazz.isPrimitive()){
            return true;
        }
        return classSet.contains(clazz);


    }
}
