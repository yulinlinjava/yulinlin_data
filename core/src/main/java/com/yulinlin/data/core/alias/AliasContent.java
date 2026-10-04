package com.yulinlin.data.core.alias;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.StringUtil;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AliasContent {

    private HashMap<String,String> fieldCache =new HashMap();


    private HashMap<String,String> columnCache =new HashMap();

    private Map<String,AliasContent> children = new HashMap<>();


    public void put(String field,String column){
        fieldCache.put(field,column);
        columnCache.put(column,field);
    }

    public void put(String field,AliasContent column){
        children.put(field,column);
    }


    public String toColumn(String field){
        return fieldCache.getOrDefault(field,field);
    }

    public String toKey(String column){
        return columnCache.getOrDefault(column,column);

    }


    public  AliasContent getChildren(String field){
        AliasContent content =  children.computeIfAbsent(field,s -> new AliasContent());
        return content;
    }

    private static Map<Class,AliasContent> globalCache = new ConcurrentHashMap<>();

    public static AliasContent newInstance(Class clazz,boolean mapUnderscoreToCamelCase) {
        return globalCache.computeIfAbsent(clazz,s -> build(s,mapUnderscoreToCamelCase));
    }

    private static AliasContent build(Class clazz,boolean mapUnderscoreToCamelCase) {
        AliasContent content = new AliasContent();
        if(clazz == null){
            return content;
        }else if(Map.class.isAssignableFrom(clazz)){
            return content;
        }


        for (Field field : ReflectionUtil.getAllDeclaredFields(clazz)) {
            JoinField joinField = AnnotationUtil.findAnnotation(field, JoinField.class);

            String name = field.getName();
            String val = name;
            if (joinField != null && joinField.exist()) {
                if (joinField.name().length() > 0) {
                    val = joinField.name();
                    if(mapUnderscoreToCamelCase){
                        val = StringUtil.javaToColumn(val);
                    }
                }else if(joinField.function().length() > 0){
                    val = joinField.function();
                }else {
                    if(mapUnderscoreToCamelCase){
                        val = StringUtil.javaToColumn(val);
                    }
                }
            }else {
                if(mapUnderscoreToCamelCase){
                    val = StringUtil.javaToColumn(val);
                }
            }
            content.put(name,val);


        }

        return content;
    }

}
