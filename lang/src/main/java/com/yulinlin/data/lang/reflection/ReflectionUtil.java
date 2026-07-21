package com.yulinlin.data.lang.reflection;


import com.yulinlin.data.lang.json.JsonUtil;
import com.yulinlin.data.lang.util.Coin;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Money;
import lombok.SneakyThrows;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReflectionUtil {


    public static boolean isEmpty(Object val) { if (val == null) return true; if (val instanceof Collection) return ((Collection<?>) val).isEmpty(); if (val instanceof Map) return ((Map<?, ?>) val).isEmpty(); if (val instanceof String) return ((String) val).isEmpty(); return false; } public static boolean isNotEmpty(Object val) { return !isEmpty(val); }
	public static <E> E newInstance(Class<E> cla) {
			return ReflectAsmUtil.newInstance(cla);
	}

    private static final Map<Class<?>, Map<String, Field>> FIELD_INDEX_CACHE = new ConcurrentHashMap<>();

    public static Field findField(Class<?> clazz, String name) {
        Map<String, Field> fieldMap = FIELD_INDEX_CACHE.computeIfAbsent(clazz, c -> {
            Map<String, Field> index = new HashMap<>();
            for (Field f : getAllDeclaredFields(c)) {
                index.put(f.getName(), f);
            }
            return index;
        });
        return fieldMap.get(name);
    }




	/**
	 * 得到所有字段
	 *
	 * @param clazz
	 * @return
	 */
	public static List<Field> getAllDeclaredFields(Class<?> clazz) {
		return ReflectAsmUtil.getAllDeclaredFields(clazz);
	}

    // 缓存 key = clazz.getName() + "#" + methodName + paramType1,paramType2...
    private static final Map<String, Method> methodCache = new ConcurrentHashMap<>();


    /**
     * 查找方法
     * @param clazz 类
     * @param methodName 方法名
     * @param parameterTypes 参数类型（可为空）
     */
    public static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String cacheKey = buildCacheKey(clazz, methodName, parameterTypes);
        Method cached = methodCache.get(cacheKey);
        if (cached != null) return cached;

        Class<?> searchType = clazz;
        while (searchType != null && searchType != Object.class) {
            Method[] methods = searchType.getDeclaredMethods();
            for (Method method : methods) {
                if (!method.getName().equals(methodName)) continue;

                if (parameterTypes == null || parameterTypes.length == 0) {
                    // 空参数类型 → 第一个匹配的方法优先返回
                    method.setAccessible(true);
                    methodCache.put(cacheKey, method);
                    return method;
                }

                // 参数类型非空 → 兼容匹配
                if (isParametersCompatible(method.getParameterTypes(), parameterTypes)) {
                    method.setAccessible(true);
                    methodCache.put(cacheKey, method);
                    return method;
                }
            }
            searchType = searchType.getSuperclass();
        }

        return null;
    }

    private static boolean isParametersCompatible(Class<?>[] methodParams, Class<?>[] argTypes) {
        if (methodParams == null) methodParams = new Class<?>[0];
        if (argTypes == null) argTypes = new Class<?>[0];
        if (methodParams.length != argTypes.length) return false;

        for (int i = 0; i < methodParams.length; i++) {
            if (!isParameterCompatible(methodParams[i], argTypes[i])) return false;
        }
        return true;
    }

    private static boolean isParameterCompatible(Class<?> methodParam, Class<?> argClass) {
        if (argClass == null) return !methodParam.isPrimitive(); // null只能匹配非基础类型
        if (methodParam.isAssignableFrom(argClass)) return true;

        if (methodParam.isPrimitive()) return primitiveToWrapper(methodParam).equals(argClass);
        if (argClass.isPrimitive()) return primitiveToWrapper(argClass).equals(methodParam);

        return false;
    }

    private static Class<?> primitiveToWrapper(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == char.class) return Character.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        return primitive;
    }

    private static String buildCacheKey(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append("#").append(methodName);
        if (parameterTypes != null && parameterTypes.length > 0) {
            sb.append("(");
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(parameterTypes[i] != null ? parameterTypes[i].getName() : "null");
            }
            sb.append(")");
        }
        return sb.toString();
    }


    @SneakyThrows
    //执行方法
    public static Object invokeMethod(Object bean, String method, Object... args) {
        return ReflectAsmUtil.invokeMethod(bean,method,args);
    }
        //执行方法

	@SneakyThrows
	//执行方法
	public static Object invokeMethod(Object bean, Method method, Object... args){

		//method.setAccessible(true);
		if(method.isDefault() ||  !Modifier.isAbstract(method.getModifiers())){
			MethodHandle methodHandle = MethodHandlesUtil.getSpecialMethodHandle(method);
			return methodHandle.bindTo(bean).invokeWithArguments(args);
		}else {

			return ReflectAsmUtil.invokeMethod(bean,method.getName(),args);
		}

	}



	@SneakyThrows
	public static Object invokeGetter(Object bean,Field field){

        return invokeGetter(bean,field.getName());

	}

	@SneakyThrows
	public static Object invokeGetter(Object bean,String name){
		try {
			return ReflectAsmUtil.invokeGetter(bean,name);
		}catch (Exception e){
			Field field =  findField(bean.getClass(),name);

			return field.get(bean);
		}





	}

	@SneakyThrows
	public static void invokeSetter(Object bean,String name,Object value) {
		try {
			ReflectAsmUtil.invokeSetter(bean,name,value);
		}catch (Exception e){
			Field field = findField(bean.getClass(), name);

			field.set(bean,value);
		}
	}

	@SneakyThrows
	public static void invokeSetter(Object bean,Field field,Object value) {

			ReflectAsmUtil.invokeSetter(bean,field.getName(),value);




	}

	public static Object parse(Object bean,String path){
		if(path.startsWith("${")){
			path = path.substring(2,path.length()-1);
			return invokeGetter(bean,path);
		}

		return path;

	}


    /** 缓存已判定的类型 */
    private static final Map<Class<?>, Boolean> PRIMITIVE_CACHE = new ConcurrentHashMap<>();

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

    /**
     * 判断对象是否是“值类型”或“原始类型”（即可直接复制）
     */
    public static boolean isPrimitive(Object obj) {
        if (obj == null) return true;
        return isPrimitiveType(obj.getClass());
    }

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


	public static <E> E clone(E obj){
		return clone(obj,false);
	}

    public static <E> E clone(E obj,boolean deep){
        if(deep == false){
            return ReflectAsmUtil.clone(obj,deep);
        }
        if(obj instanceof Collection){
            Collection coll =(Collection) obj;
            if(coll.size() < 500){
                return ReflectAsmUtil.clone(obj,deep);
            }
        }
        return JsonUtil.clone(obj);
    }

	public static boolean hasField(Class clazz,String name){
		for (Field field : getAllDeclaredFields(clazz)) {
			if(field.getName().equals(name)){
				return true;
			}
		}
		return false;


	}



	public static <K,V> V copyProperties(K bean,V target) {
		 ReflectAsmUtil.copyProperties(bean,target);
         return target;
	}

	public static void main(String[] args) {
		int [] as = {1,2,3};

		Object clone = isPrimitive(new Date());

		int a = 0;
	}


}