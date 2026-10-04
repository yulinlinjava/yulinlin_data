package com.yulinlin.data.lang.reflection;


import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDK 25 通过 {@link MethodHandles#privateLookupIn(Class, MethodHandles.Lookup)}
 * 创建具备私有访问权限的查找对象。
 */
public class MethodHandlesUtil {

    public static MethodHandles.Lookup lookup(Class<?> callerClass) {
        try {
            return MethodHandles.privateLookupIn(callerClass, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot create a private lookup for " + callerClass.getName(), e);
        }
    }

    private static ConcurrentHashMap<Method,MethodHandle> cache = new ConcurrentHashMap();

    public static MethodHandle getSpecialMethodHandle(Method parentMethod) {
        MethodHandle handle = cache.computeIfAbsent(parentMethod,(method) ->{
            final Class<?> declaringClass = method.getDeclaringClass();
            MethodHandles.Lookup lookup = lookup(declaringClass);
            try {
                return lookup.unreflectSpecial(method, declaringClass);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        return handle;

    }
}
