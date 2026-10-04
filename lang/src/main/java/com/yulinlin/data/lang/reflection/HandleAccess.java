package com.yulinlin.data.lang.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed-signature adapters are created once and never bound to a bean instance. */
final class HandleAccess {
    static final MethodType GET = MethodType.methodType(Object.class, Object.class);
    static final MethodType SET = MethodType.methodType(void.class, Object.class, Object.class);
    private static final ClassValue<Map<Member, MethodHandle>> HANDLES = new ClassValue<>() {
        protected Map<Member, MethodHandle> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<MethodHandle> CONSTRUCTORS = new ClassValue<>() {
        protected MethodHandle computeValue(Class<?> type) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.trySetAccessible();
                return MethodHandles.lookup().unreflectConstructor(constructor)
                        .asType(MethodType.methodType(Object.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Accessible no-argument constructor required: " + type.getName(), e);
            }
        }
    };
    private static final ClassValue<Map<Method, MethodHandle>> INVOKERS = new ClassValue<>() {
        protected Map<Method, MethodHandle> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    static MethodHandle method(Method method) {
        return HANDLES.get(method.getDeclaringClass()).computeIfAbsent(method, key -> {
            try {
                method.trySetAccessible();
                return MethodHandles.lookup().unreflect(method).asFixedArity();
            } catch (IllegalAccessException e) { throw new IllegalStateException("Cannot access " + method, e); }
        });
    }

    static MethodHandle getter(Method method) { return receiver(method(method), method).asType(GET); }
    static MethodHandle setter(Method method) { return receiver(method(method), method).asType(SET); }
    private static MethodHandle receiver(MethodHandle handle, Method method) {
        return Modifier.isStatic(method.getModifiers()) ? MethodHandles.dropArguments(handle, 0, Object.class) : handle;
    }

    static MethodHandle field(Field field, boolean write) {
        if (write && Modifier.isFinal(field.getModifiers())) throw new IllegalArgumentException("Cannot write final field: " + field);
        try {
            field.trySetAccessible();
            MethodHandle handle = write ? MethodHandles.lookup().unreflectSetter(field) : MethodHandles.lookup().unreflectGetter(field);
            if (Modifier.isStatic(field.getModifiers())) handle = MethodHandles.dropArguments(handle, 0, Object.class);
            return handle.asType(write ? SET : GET);
        } catch (IllegalAccessException e) { throw new IllegalStateException("Cannot access " + field, e); }
    }

    static Object construct(Class<?> type) {
        try { return (Object) CONSTRUCTORS.get(type).invokeExact(); }
        catch (Throwable e) { return fail(e); }
    }
    static Object get(MethodHandle handle, Object bean) {
        try { return (Object) handle.invokeExact(bean); }
        catch (Throwable e) { return fail(e); }
    }
    static void set(MethodHandle handle, Object bean, Object value) {
        try { handle.invokeExact(bean, value); }
        catch (Throwable e) { HandleAccess.<RuntimeException, Object>fail(e); }
    }
    static Object invoke(Object bean, Method method, Object[] args) {
        MethodHandle handle = INVOKERS.get(method.getDeclaringClass()).computeIfAbsent(method, key ->
                receiver(method(key).asSpreader(Object[].class, key.getParameterCount()), key)
                        .asType(MethodType.methodType(Object.class, Object.class, Object[].class)));
        try { return (Object) handle.invokeExact(bean, args); }
        catch (Throwable e) { return fail(e); }
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable, T> T fail(Throwable e) throws E { throw (E) e; }
}
