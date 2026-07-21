package com.yulinlin.data.lang.reflection;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import com.esotericsoftware.reflectasm.MethodAccess;
import com.esotericsoftware.reflectasm.FieldAccess;
import com.yulinlin.data.lang.util.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class ReflectAsmUtil {

    // ---------------- 全局缓存 ----------------
    private static final Map<Class<?>, MethodAccess> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, FieldAccess> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ConstructorAccess<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Integer>> METHOD_INDEX_CACHE = new ConcurrentHashMap<>();

    private static final String SET_PREFIX = "set";
    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";
    public static final String SP_PREFIX = "\\.";

    /** 认为是“值类型”的基类集合 */
    private static final Set<Class<?>> VALUE_TYPES = new HashSet<>(Arrays.asList(
            String.class,
            Number.class,
            Boolean.class,
            Character.class,
            Date.class,
            java.sql.Date.class,
            java.time.temporal.Temporal.class,
            java.math.BigDecimal.class,
            java.math.BigInteger.class,
            java.util.UUID.class
    ));

    /** 你自定义的类型 */
    static {
        VALUE_TYPES.add(DateTime.class);
        VALUE_TYPES.add(Money.class);
        VALUE_TYPES.add(Coin.class);
    }

    public static boolean isPrimitive(Object obj) {
        if (obj == null) return true;
        return isPrimitiveType(obj.getClass());
    }
    private static final Map<Class<?>, Boolean> PRIMITIVE_CACHE = new ConcurrentHashMap<>();

    /**
     * 判断类型是否是“值类型”或“原始类型”
     */
    public static boolean isPrimitiveType(Class<?> type) {
        if (type == null) return true;

        // 缓存命中
        Boolean cached = PRIMITIVE_CACHE.get(type);
        if (cached != null) return cached;

        boolean result;

        if (type.isPrimitive()
                || type.isEnum()
                || VALUE_TYPES.contains(type)) {
            result = true;
        } else {
            // 判断是否是这些类型的子类
            result = VALUE_TYPES.stream().anyMatch(base -> base.isAssignableFrom(type))
                    // 常见的 java.lang 值类型
                    || type.getName().startsWith("java.lang.")
                    // Class、Void 等特殊类型
                    || type == Class.class
                    || type == Void.class;
        }

        PRIMITIVE_CACHE.put(type, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T clone(T source, boolean deep) {
        if (source == null) return null;

        // 基础类型直接返回
        if (isPrimitiveType(source.getClass())) return source;

        if(source instanceof LongAdder){
            LongAdder longAdder = new LongAdder();
            LongAdder s = (LongAdder) source;
            longAdder.add(s.longValue()
            );
            return (T)longAdder;
        }
        if(source instanceof DoubleAdder){
            DoubleAdder longAdder = new DoubleAdder();
            DoubleAdder s = (DoubleAdder) source;
            longAdder.add(s.doubleValue());
            return (T)longAdder;
        }

        // Map
        if (source instanceof Map) {
            Map<Object, Object> orig = (Map<Object, Object>) source;
            Map<Object, Object> copy = (Map<Object,Object>) newInstance(source.getClass());
            for (Map.Entry<Object, Object> entry : orig.entrySet()) {

                copy.put(entry.getKey(), deep ? clone(entry.getValue(), true) : entry.getValue());
            }
            return (T) copy;
        }

        if(source instanceof ListString){
            ListString listString =   new ListString<>();
            listString.addAll((ListString)source);
            return (T)listString;
        }

        // List
        if (source instanceof Collection) {
            List<Object> orig = (List<Object>) source;
            List<Object> copy =(List<Object>) newInstance(source.getClass());
            for (Object item : orig) {

                    copy.add(deep ? clone(item, true) : item);


            }
            return (T) copy;
        }


        // Array
        if (source.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(source);
            Object copy = java.lang.reflect.Array.newInstance(source.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Object val = java.lang.reflect.Array.get(source, i);
                java.lang.reflect.Array.set(copy, i, deep ? clone(val, true) : val);
            }
            return (T) copy;
        }

        // 普通 Bean
        Class<?> clazz = source.getClass();
        T target = newInstance((Class<T>) clazz);

        getAllDeclaredFields(clazz)

                .forEach(m -> {
                    String prop = m.getName();
                    Object val = invokeGetter(source, prop);
                    if(val != null){
                        if(!isPrimitive(val)){
                            if (deep) val = clone(val, true);
                        }
                        invokeSetter(target, prop, val);
                    }

                });

        return target;
    }



    private static Map<Class, List<Field>> fieldMap = new ConcurrentHashMap<>();

    private static void getAllDeclaredFields(Class clazz,List list){
        if(clazz == Object.class){
            return;
        }
        getAllDeclaredFields(clazz.getSuperclass(),list);
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            list.add(field);
        }
    }

    /**
     * 得到所有字段
     *
     * @param clazz
     * @return
     */
    public static List<Field> getAllDeclaredFields(Class<?> clazz) {
        return fieldMap.computeIfAbsent(clazz,(data) -> {
            ArrayList list = new ArrayList<>();
            getAllDeclaredFields(data,list);
            return list;
        });
    }


    public static <K,V> V copyProperties(K from,V target) {

        for (Field field : getAllDeclaredFields(target.getClass())) {
            Object o = invokeGetter(from, field.getName());

            if(o != null){
                invokeSetter(target,field.getName(),o);
            }
        }
        return target;
    }

        // ------------------ Constructor / Method / Field ------------------
    private static <T> ConstructorAccess<T> forConstructor(Class<T> clazz) {
        return (ConstructorAccess<T>) CONSTRUCTOR_CACHE.computeIfAbsent(clazz, ConstructorAccess::get);
    }

    private static FieldAccess forField(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, FieldAccess::get);
    }

    private static MethodAccess forMethod(Class<?> clazz) {
        return METHOD_CACHE.computeIfAbsent(clazz, MethodAccess::get);
    }

    private static Map<String, Integer> basemethodIndex(Class<?> clazz) {
        // 如果已经有类的索引缓存，直接返回
        return METHOD_INDEX_CACHE.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
    }

    /**
     * 获取指定方法名的索引，如果不存在则懒加载计算
     */
    private static int getMethodIndex(Class<?> clazz, String methodName) {
        Map<String, Integer> indexMap = basemethodIndex(clazz);
        return indexMap.computeIfAbsent(methodName, name -> {
            MethodAccess access = forMethod(clazz);
            try {
                return access.getIndex(name);
            } catch (Exception e) {
               return -1;
            }
        });
    }

    // ------------------ clear ------------------
    public static void clear() {
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        CONSTRUCTOR_CACHE.clear();
        METHOD_INDEX_CACHE.clear();
    }

    // ------------------ newInstance ------------------
    public static <E> E newInstance(Class<E> clazz) {
        return forConstructor(clazz).newInstance();
    }

    // ------------------ invokeMethod ------------------
    public static Object invokeMethod(Object bean, String name, Object... args) {
        if (bean == null || name == null) return null;

        int index = getMethodIndex(bean.getClass(),name);
        if (index == -1) throw new RuntimeException("Method not found: " + name);
        return forMethod(bean.getClass()).invoke(bean, index, args);
    }

    // ------------------ getter / setter ------------------
    public static Object invokeGetter(Object bean, String path) {
        if (bean == null || path == null) return null;
        String[] parts = path.split(SP_PREFIX);
        for (String part : parts) {
            if (bean == null) return null;

            // Map
            if (bean instanceof Map) {
                bean = ((Map<?, ?>) bean).get(part);
                continue;
            }

            // List / Array 支持索引
            if (bean instanceof List) {
                int idx = Integer.parseInt(part);
                bean = ((List<?>) bean).get(idx);
                continue;
            }
            if (bean.getClass().isArray()) {
                int idx = Integer.parseInt(part);
                bean = ((Object[]) bean)[idx];
                continue;
            }

            // 普通 Bean
            bean = getForMethod(bean, part);
        }
        return bean;
    }

    public static void invokeSetter(Object bean, String path, Object value) {
        if (bean == null || path == null) return;
        String[] parts = path.split(SP_PREFIX);
        for (int i = 0; i < parts.length - 1; i++) {
            bean = invokeGetter(bean, parts[i]);
            if (bean == null) return;
        }
        String last = parts[parts.length - 1];

        // Map
        if (bean instanceof Map) {
            ((Map) bean).put(last, value);
            return;
        }

        // List / Array
        if (bean instanceof List) {
            int idx = Integer.parseInt(last);
            List<Object> list = (List<Object>) bean;
            if (idx < list.size()) list.set(idx, value);
            else list.add(value);
            return;
        }
        if (bean.getClass().isArray()) {
            int idx = Integer.parseInt(last);
            ((Object[]) bean)[idx] = value;
            return;
        }

        // 普通 Bean
        setForMethod(bean, last, value);
    }

    // ------------------ Bean 内部方法 ------------------
    private static Object getForMethod(Object bean, String name) {
        MethodAccess access = forMethod(bean.getClass());
        String getterName = GET_PREFIX + StringUtil.toUpperCaseFirstOne(name);

        int index = getMethodIndex(bean.getClass(),getterName);

        if (index == -1) {
            getterName = IS_PREFIX + StringUtil.toUpperCaseFirstOne(name);
            index = getMethodIndex(bean.getClass(),getterName);
        }
        if (index == -1) return null;
        return access.invoke(bean, index);
    }

    private static void setForMethod(Object bean, String name, Object value) {
        MethodAccess access = forMethod(bean.getClass());


        String setterName = SET_PREFIX + StringUtil.toUpperCaseFirstOne(name);
        int index =  getMethodIndex(bean.getClass(),setterName);
        if (index == -1) throw new RuntimeException("Setter not found: " + setterName);

        access.invoke(bean, index, value);
    }
}
