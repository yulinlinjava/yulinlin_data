package com.yulinlin.data.lang.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public  class BaseGeneric {

    protected BaseGeneric prev;

    protected Type type;

    public BaseGeneric(BaseGeneric prev, Type type) {
        this.prev = prev;
        this.type = type;
    }

    public BaseGeneric(Type type) {
        this.type = type;
    }




    public Class getRawType(){
        if(type instanceof Class){
            return (Class) type;
        }
        if(type instanceof ParameterizedType){
            ParameterizedType parameterizedType = (ParameterizedType)type;
            return (Class)parameterizedType.getRawType();
        }
        else if(type instanceof TypeVariable){
            TypeVariable typeVariable =(TypeVariable)type;
            return getGeneric(typeVariable.getName()).getRawType();
        }

        return null;



    }


    //参数化类型获取多级泛型
    protected   BaseGeneric getGeneric(ParameterizedType parameterizedType , int... is){

        for (int i : is) {
            Type genericType = parameterizedType.getActualTypeArguments()[i];
            if(genericType instanceof ParameterizedType){
                parameterizedType = (ParameterizedType)genericType;

            }

            else if(genericType instanceof TypeVariable){

                TypeVariable typeVariable =  (TypeVariable)genericType;
                return getGeneric(typeVariable.getName());
            }

            else if(genericType instanceof Class){
                BaseGeneric node = new BaseGeneric(genericType);
                node.prev =this;
                return node;
            }
        }

        BaseGeneric node = new BaseGeneric(type);
        node.prev =this;
        return node;



    }


    //得到类泛型
    public BaseGeneric getGeneric(int... is){

        if(type instanceof ParameterizedType){
            ParameterizedType parameterizedType = (ParameterizedType)type;
            return getGeneric(parameterizedType,is);
        }else if(type instanceof TypeVariable){
            return prev.getGeneric(((TypeVariable) type).getName());

        }

        return null;
    }


    //根据名字得到泛型
    public BaseGeneric getGeneric(String name){

        if(prev == null){
            return null;
        }
        Class clazz =  getRawType();
        int i = 0;
        for (TypeVariable typeVariable : clazz.getTypeParameters()) {

            if(typeVariable.getName().equals(name)){
                ParameterizedType parameterizedType = (ParameterizedType)type;
                return prev.getGeneric(parameterizedType,i);
            }
            i++;
        }

        return prev.getGeneric(name);



    }




    }
