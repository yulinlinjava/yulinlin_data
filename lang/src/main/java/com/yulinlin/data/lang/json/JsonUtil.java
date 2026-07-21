package com.yulinlin.data.lang.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Collection;


public class JsonUtil {

    private static ObjectMapper mapper;

    static {
        mapper=new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.registerModule(new YulinlinSimpModule());



    }

    public static void setMapper(ObjectMapper mapper) {
        JsonUtil.mapper = mapper;
    }

    public static ObjectMapper getMapper(){
        return mapper;
    }


    public static <E> E clone(E data){
        String s = toJson(data);
        Object val;
        if(data instanceof Collection){
            Collection coll = (Collection)data;
            if(coll.size() > 0){
                Object next = coll.iterator().next();
                val =  parseJson(s,data.getClass(),next.getClass());
            }else {
                val =  parseJson(s,data.getClass());
            }

        }else {
            val =  parseJson(s,data.getClass());
        }

        return (E)val;
    }
    @SneakyThrows
    public static String toJson(Object obj)  {

        ObjectMapper mapper =getMapper();

            return mapper.writeValueAsString(obj);

    }

    @SneakyThrows
    public static <T> T parseJson(String json,TypeReference<T> typeReference) {
        return getMapper().readValue(json,typeReference);
    }
    public static <T> T to(Object from,Class<T> cla,Class<?>... clazz) {
        return parseJson(toJson(from),cla,clazz);
    }

    public static <T> T parseJson(String json,Class<T> cla,Class<?>... clazz) {

        try {
            if(clazz.length == 0){
                return mapper.readValue(json,cla);
            }else{
                ObjectMapper mapper =getMapper();
                JavaType javaType =  mapper.getTypeFactory()
                        .constructParametricType(cla, clazz);
                return mapper.readValue(json,javaType);
            }


        } catch (Exception e) {
          throw  new RuntimeException(e);
        }
    }


    private static <T> T parseParameterizedType(String json,ParameterizedType parameterizedType) {
        try {
            ObjectMapper objectMapper =getMapper();
            JavaType javaType=objectMapper.getTypeFactory().constructType(parameterizedType);
            return  objectMapper.readValue(json, javaType);
        }catch (Exception e){
            throw  new RuntimeException(e);
        }
    }

    public static <T> T parseByField(String json,Field f) {
        Type clazz =  f.getGenericType();
        if(clazz instanceof ParameterizedType){
            ParameterizedType type = (ParameterizedType)  clazz;
            return  parseParameterizedType(json,type);
        }else {
            Class c =  f.getType();

            return (T)parseJson(json,c);
        }

    }


/*
    public static <T> T parseByClass(String json,Class f) {
        ParameterizedType type = (ParameterizedType)  f.getGenericSuperclass();
        return  parseParameterizedType(json,type);
    }

*/




}

