package com.yulinlin.data.lang.reflection;

import com.yulinlin.data.lang.util.Coin;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Money;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Keeps ORM value classification separate from shallow-copy handling of mutable values. */
final class ValueCopies {
    private static final ClassValue<Boolean> VALUES = new ClassValue<>() {
        protected Boolean computeValue(Class<?> type) {
            return type.isPrimitive() || type.isEnum() || type.getName().startsWith("java.lang.")
                    || Number.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type)
                    || java.time.temporal.Temporal.class.isAssignableFrom(type) || type == UUID.class
                    || DateTime.class.isAssignableFrom(type) || Money.class.isAssignableFrom(type)
                    || Coin.class.isAssignableFrom(type);
        }
    };
    static boolean isValueType(Class<?> type) { return type == null || VALUES.get(type); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T shallow(T source) {
        if (source instanceof Date date) return (T) date.clone();
        if (source instanceof AtomicInteger value) return (T) new AtomicInteger(value.get());
        if (source instanceof AtomicLong value) return (T) new AtomicLong(value.get());
        if (source instanceof LongAdder value) { LongAdder copy = new LongAdder(); copy.add(value.sum()); return (T) copy; }
        if (source instanceof DoubleAdder value) { DoubleAdder copy = new DoubleAdder(); copy.add(value.sum()); return (T) copy; }
        if (source.getClass().isArray()) {
            int length = Array.getLength(source);
            Object copy = Array.newInstance(source.getClass().getComponentType(), length);
            System.arraycopy(source, 0, copy, 0, length);
            return (T) copy;
        }
        if (source instanceof TreeSet<?> set) return (T) set.clone();
        if (source instanceof TreeMap<?, ?> map) return (T) map.clone();
        if (source instanceof EnumSet<?> set) return (T) set.clone();
        if (source instanceof EnumMap<?, ?> map) return (T) map.clone();
        if (source instanceof Collection<?> collection) {
            Collection copy = (Collection) ReflectionUtil.newInstance(source.getClass());
            copy.addAll(collection);
            return (T) copy;
        }
        if (source instanceof Map<?, ?> map) {
            Map copy = (Map) ReflectionUtil.newInstance(source.getClass());
            copy.putAll(map);
            return (T) copy;
        }
        if (isValueType(source.getClass())) return source;
        T copy = (T) ReflectionUtil.newInstance(source.getClass());
        for (Field field : ReflectionUtil.getAllDeclaredFields(source.getClass())) {
            ReflectionUtil.PropertyAccess access = ReflectionUtil.property(field);
            access.set(copy, access.get(source));
        }
        return copy;
    }
}
