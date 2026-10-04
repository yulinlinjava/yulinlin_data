package com.yulinlin.data.lang.util;


import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class GenericMethod {

    protected BaseGeneric prev;

    private Method method;


    public GenericMethod(BaseGeneric prev, Method method) {
        this.prev = prev;
        this.method = method;
    }

    public  GenericClass asMethodReturnType(){
        Type type =  method.getGenericReturnType();
        return new GenericClass(prev,type);
    }

    public  GenericClass asMethodParameterType(int i ){
        Type type =  method.getGenericParameterTypes()[i];
        GenericClass genericClass =  new GenericClass(prev,type);


        return genericClass;
    }

}
