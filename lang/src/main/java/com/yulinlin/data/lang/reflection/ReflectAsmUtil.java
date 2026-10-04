package com.yulinlin.data.lang.reflection;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Compatibility facade. Implementation now uses JDK method handles, not ReflectASM.
 * New code should use ReflectionUtil.
 */
@Deprecated
public class ReflectAsmUtil {
    public static final String SP_PREFIX = "\\.";
    public static <E> E newInstance(Class<E> type) { return ReflectionUtil.newInstance(type); }
    public static Object invokeMethod(Object bean, String name, Object... args) { return ReflectionUtil.invokeMethod(bean, name, args); }
    public static Object invokeGetter(Object bean, String path) { return ReflectionUtil.invokeGetter(bean, path); }
    public static void invokeSetter(Object bean, String path, Object value) { ReflectionUtil.invokeSetter(bean, path, value); }
    public static List<Field> getAllDeclaredFields(Class<?> type) { return ReflectionUtil.getAllDeclaredFields(type); }
    public static boolean isPrimitive(Object value) { return ReflectionUtil.isPrimitive(value); }
    public static boolean isPrimitiveType(Class<?> type) { return ReflectionUtil.isPrimitiveType(type); }
    public static <T> T clone(T source, boolean deep) { return ReflectionUtil.clone(source, deep); }
    public static <K, V> V copyProperties(K source, V target) { return ReflectionUtil.copyProperties(source, target); }
    /** No ReflectASM caches remain. ClassValue metadata is associated with class lifetime. */
    public static void clear() { }
}
