package com.yulinlin.data.lang.reflection;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Per-operation identity tracking preserves aliases and bean/list/map-value cycles. */
final class DeepCopies {
    private final IdentityHashMap<Object, Object> copies = new IdentityHashMap<>();
    private static final Set<Class<?>> IMMUTABLE = Set.of(
            String.class, Boolean.class, Character.class, Byte.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class, BigInteger.class,
            BigDecimal.class, UUID.class, Class.class);

    static <T> T copyProperties(Object source, T target) {
        DeepCopies context = new DeepCopies();
        context.copies.put(source, target);
        ReflectionUtil.copyMatchingProperties(source, target, context);
        return target;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object copy(Object value) {
        if (value == null) return null;
        if (copies.containsKey(value)) return copies.get(value);
        Class<?> type = value.getClass();
        if (IMMUTABLE.contains(type) || value instanceof Enum<?>
                || (type.getPackageName().equals("java.time")
                    && type.getClassLoader() == null)) return value;
        if (value instanceof Date date) return remember(value, date.clone());
        if (value instanceof Calendar calendar) return remember(value, calendar.clone());
        if (value instanceof StringBuilder text) return remember(value, new StringBuilder(text));
        if (value instanceof StringBuffer text) return remember(value, new StringBuffer(text));
        if (value instanceof AtomicInteger number) return remember(value, new AtomicInteger(number.get()));
        if (value instanceof AtomicLong number) return remember(value, new AtomicLong(number.get()));
        if (value instanceof AtomicBoolean flag) return remember(value, new AtomicBoolean(flag.get()));
        if (value instanceof LongAdder number) {
            LongAdder result = new LongAdder(); result.add(number.sum()); return remember(value, result);
        }
        if (value instanceof DoubleAdder number) {
            DoubleAdder result = new DoubleAdder(); result.add(number.sum()); return remember(value, result);
        }
        if (value instanceof AtomicReference<?> reference) {
            AtomicReference<Object> result = new AtomicReference<>();
            remember(value, result);
            result.set(copy(reference.get()));
            return result;
        }
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object result = remember(value, Array.newInstance(type.getComponentType(), length));
            if (type.getComponentType().isPrimitive()) System.arraycopy(value, 0, result, 0, length);
            else for (int i = 0; i < length; i++) Array.set(result, i, copy(Array.get(value, i)));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map result;
            if (map instanceof EnumMap enumMap) { result = new EnumMap(enumMap); result.clear(); }
            else if (map instanceof SortedMap sorted) result = new TreeMap(sorted.comparator());
            else if (map instanceof IdentityHashMap) result = new IdentityHashMap();
            else {
                Object empty = emptyContainer(type);
                result = empty == null ? new LinkedHashMap() : (Map) empty;
            }
            remember(value, result);
            for (Map.Entry<?, ?> entry : map.entrySet()) result.put(copy(entry.getKey()), copy(entry.getValue()));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            Collection result;
            if (collection instanceof EnumSet enumSet) { result = enumSet.clone(); result.clear(); }
            else if (collection instanceof SortedSet sorted) result = new TreeSet(sorted.comparator());
            else if (collection instanceof PriorityQueue queue) result = new PriorityQueue(Math.max(1, queue.size()), queue.comparator());
            else {
                Object empty = emptyContainer(type);
                if (empty != null) result = (Collection) empty;
                else if (collection instanceof Set<?>) result = new LinkedHashSet();
                else if (collection instanceof Queue<?>) result = new LinkedList();
                else result = new ArrayList();
            }
            remember(value, result);
            for (Object element : collection) result.add(copy(element));
            return result;
        }
        if (type.isRecord()) throw new IllegalArgumentException("Deep copy of record requires an explicit mapper: " + type.getName());
        Object result = remember(value, ReflectionUtil.newInstance(type));
        // Copy nulls as well inside a new nested bean, overriding constructor defaults.
        for (var field : ReflectionUtil.getAllDeclaredFields(type)) {
            if (field.isSynthetic()) continue;
            var access = ReflectionUtil.property(field);
            access.set(result, copy(access.get(value)));
        }
        return result;
    }

    private Object remember(Object source, Object target) {
        copies.put(source, target);
        return target;
    }

    private static Object emptyContainer(Class<?> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) return null;
            return ReflectionUtil.newInstance(type);
        } catch (NoSuchMethodException e) {
            return null; // e.g. List.of(), Arrays.asList() and unmodifiable wrappers
        }
    }
}
