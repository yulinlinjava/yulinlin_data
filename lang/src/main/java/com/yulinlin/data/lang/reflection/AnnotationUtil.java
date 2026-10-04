package com.yulinlin.data.lang.reflection;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注解工具类
 */
public class AnnotationUtil {




    /**
     * 查询字段注解
     * @param field
     * @param anno
     * @param <E>
     * @return
     */
    public static <E extends Annotation> E findAnnotation(Field field, Class<E> anno){

        return AnnotationUtils.findAnnotation(field,anno);


    }


    /**
     * 查询类注解
     * @param clazz
     * @param anno
     * @param <E>
     * @return
     */
    public static <E extends Annotation> E findAnnotation(Class clazz, Class<E> anno){

        return AnnotationUtils.findAnnotation(clazz,anno);

    }

    /**
     * 查询方法注解
     * @param method
     * @param anno
     * @param <E>
     * @return
     */
    public static <E extends Annotation> E findAnnotation(Method method, Class<E> anno){
        return AnnotationUtils.findAnnotation(method,anno);

    }

    public static  <E extends Annotation> E findAnnotation(ProceedingJoinPoint pjp , Class<E> clazz){
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();

        E anno = findAnnotation(      methodSignature.getMethod(),clazz);

        if(anno == null){
            anno = findAnnotation(     pjp.getTarget().getClass(),clazz);

        }
        return anno;


    }




    /**
     * 查询方法参数注解
     * @param method
     * @param anno
     * @param i
     * @param <E>
     * @return
     */
    public static <E extends Annotation> E findAnnotation(Method method, Class<E> anno,int i){

        return method.getParameterTypes()[i].getAnnotation(anno);
    }


    /**
     * 查询方法或类注解
     * @param method
     * @param anno
     * @param <E>
     * @return
     */
    public static <E extends Annotation> E findMethodAnnotation(Method method, Class<E> anno){
       E e = findAnnotation(method,anno);
       if(e != null){
           return  e;
       }
       return findAnnotation(method.getDeclaringClass(),anno);
    }

}
