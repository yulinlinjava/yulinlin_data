package com.yulinlin.data.lang.util;

import lombok.SneakyThrows;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GenericClass extends BaseGeneric {

    private List<GenericClass> next = new ArrayList<>() ;

     GenericClass(BaseGeneric prev, Type type) {
        super(prev, type);
    }


     GenericClass(Type type) {
        super(type);
        init();
    }


    private static Field findField(Class clazz,String name){
         if(clazz == null){
             return null;
         }
         try {
            return   clazz.getDeclaredField(name);
         }catch (Exception e){
         }

         return findField(clazz.getSuperclass(),name);
    }


    /**
     * 转换字段
     * @return
     */
    @SneakyThrows
    public  GenericField asField(String name) {

        Class entity =  getRawType();
        Field field = findField(entity,name);
        Type type =  field.getGenericType();

        return new GenericField(this,type);
    }

    /**
     * 转换方法
     * @return
     */
  /*  @SneakyThrows
    public  GenericMethod asMethod(String name,Class... types) {

        if(type == null){
            return null;
        }
        try {
            Method method =  getRawType().getMethod(name,types);
            return new GenericMethod(this,method);
        }catch (Exception e){

        }
        GenericClass p = (GenericClass)prev;
        return p.asMethod(name,types);

    }*/

    public  GenericMethod asMethod(Method method) {

        GenericClass p =  as(method.getDeclaringClass());

        return new GenericMethod(p,method);
    }

    public GenericClass asLast() {


        if(next != null && next.size() > 0){
            return  next.get(0).asLast();
        }

        return (GenericClass)this.prev;
    }


        public GenericClass as(Class target){



        if(getRawType() == target){
            return  this;
        }
        for (GenericClass node : next) {
            GenericClass val =  node.as(target);
            if(val != null){
                return val;
            }
        }
        return null;
    }

    private GenericClass buildNext(Type type){


        GenericClass node = new GenericClass(this,type);
        next.add(node);
        node.init();
        return node;
    }

    private void init(Type type){
        if(type instanceof Class){
            Class clazz =(Class)type;
            if(clazz.getGenericSuperclass() != Object.class){
                this.buildNext(clazz.getGenericSuperclass());
            }

            for (Type type1 : clazz.getGenericInterfaces()) {
                this.buildNext(type1);
            }
        }
    }

    protected List<GenericClass> init(){
        List list =  new ArrayList<>();
        if(type instanceof Class){
            Class clazz =(Class)type;
            init(type);
        }else if(type instanceof ParameterizedType){
            ParameterizedType parameterizedType = (ParameterizedType)type;
            Type rawType =  parameterizedType.getRawType();
            init(rawType);
        }
        return list;
    }



    private static ConcurrentHashMap<Type,GenericClass> cache = new ConcurrentHashMap();


    public static GenericClass newInstance(Class clazz){
     return    cache.computeIfAbsent(clazz,type -> {
            return new GenericClass(type);
        });
    }



}
