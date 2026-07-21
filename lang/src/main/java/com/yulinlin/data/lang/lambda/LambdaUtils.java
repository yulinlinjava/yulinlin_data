package com.yulinlin.data.lang.lambda;




import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.StringUtil;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LambdaUtils {

    /**
     * SerializedLambda 反序列化缓存
     */
    private static final Map<Class<?>, SerializedLambda> cache = new ConcurrentHashMap<>();

    /**
     * 获取序列化对象
     * @param fn
     * @return
     */
    public static  SerializedLambda serializedLambda(Object fn) {
        Class key =  fn.getClass();
        SerializedLambda lambda =  cache.get(key);
        if(lambda == null){
            try {
                Method method = key.getDeclaredMethod("writeReplace");
                method.setAccessible(Boolean.TRUE);
                lambda= (SerializedLambda) method.invoke(fn);
                cache.put(key,lambda);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return  lambda;
    }


    /**
     * 获取方法名
     * @param fn
     * @return
     */
    public static  String lambdaMethodName(Object fn) {

            SerializedLambda serializedLambda = serializedLambda(fn);
            String getter = serializedLambda.getImplMethodName();
            return getter;

    }

    public static <E> Class<E> lambdaMethodNameToClassName(Object fn) {
        String name =  serializedLambda(fn).getInstantiatedMethodType();
        int start =  name.indexOf("L")+1;
        int end =  name.indexOf(";)");
        String className =  name.substring(start,end);
        className =   className.replace("/",".");
        try {
            Class clazz = Class.forName(className);
            return  clazz;
        }catch (ClassNotFoundException e){
            throw new RuntimeException("类不存在："+className);
        }
    }

    public static  Field lambdaMethodNameToField(Object fn) {
        Class<Object> objectClass = lambdaMethodNameToClassName(fn);

        String name =  lambdaMethodName(fn);
            if(name.startsWith("get")){
                name =  name.substring(3);
            }else if(name.startsWith("is")){
                name =  name.substring(2);
            }else if(name.startsWith("set")){
                name =  name.substring(3);
            }else{

            }
            name =    StringUtil.toLowerCaseFirstOne(name);

         return ReflectionUtil.findField(objectClass,name);
    }

}
