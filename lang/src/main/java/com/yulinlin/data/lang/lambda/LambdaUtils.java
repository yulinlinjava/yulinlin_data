package com.yulinlin.data.lang.lambda;




import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.StringUtil;

import java.lang.invoke.SerializedLambda;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class LambdaUtils {

    /** Caches only the accessor, never a SerializedLambda that may retain captured arguments. */
    private static final ClassValue<MethodHandle> WRITE_REPLACE = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                Method method = type.getDeclaredMethod("writeReplace");
                method.trySetAccessible();
                return MethodHandles.lookup().unreflect(method)
                        .asType(MethodType.methodType(Object.class, Object.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Not a serializable lambda: " + type.getName(), e);
            }
        }
    };

    /**
     * 获取序列化对象
     * @param fn
     * @return
     */
    public static  SerializedLambda serializedLambda(Object fn) {
        if (fn == null) throw new IllegalArgumentException("lambda must not be null");
        try {
            Object serialized = (Object) WRITE_REPLACE.get(fn.getClass()).invokeExact((Object) fn);
            return (SerializedLambda) serialized;
        } catch (Throwable error) {
            return LambdaUtils.<RuntimeException, SerializedLambda>fail(error);
        }
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

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, T> T fail(Throwable error) throws E {
        throw (E) error;
    }

}
