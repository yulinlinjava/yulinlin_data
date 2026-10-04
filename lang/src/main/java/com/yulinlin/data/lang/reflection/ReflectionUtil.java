package com.yulinlin.data.lang.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Cached reflection helpers. Accessor exceptions propagate without field fallback. */
public class ReflectionUtil {
    private static final ClassValue<Metadata> METADATA = new ClassValue<>() {
        @Override protected Metadata computeValue(Class<?> type) { return new Metadata(type); }
    };

    private record Signature(String name, List<Class<?>> types) {}
    private static final class Metadata {
        final List<Field> fields;
        final Map<String, Field> fieldIndex = new HashMap<>();
        final List<Method> methods;
        final Map<Signature, Optional<Method>> resolved = new ConcurrentHashMap<>();
        final Map<String, Optional<Method>> getters = new ConcurrentHashMap<>();
        final Set<String> setterNames = new HashSet<>();
        final Map<String, PropertyAccess> properties = new ConcurrentHashMap<>();
        final Map<Field, PropertyAccess> fieldProperties = new ConcurrentHashMap<>();

        Metadata(Class<?> type) {
            List<Field> all = new ArrayList<>();
            collectFields(type, all);
            fields = List.copyOf(all);
            for (Field field : fields) fieldIndex.put(field.getName(), field);
            LinkedHashMap<Signature, Method> index = new LinkedHashMap<>();
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) addMethod(index, method);
            }
            // Includes inherited public interface/default methods.
            for (Method method : type.getMethods()) addMethod(index, method);
            methods = List.copyOf(index.values());
            for (Method method : methods) {
                if (method.getParameterCount() == 1) setterNames.add(method.getName());
            }
        }
    }

    private static void collectFields(Class<?> type, List<Field> fields) {
        if (type == null || type == Object.class) return;
        collectFields(type.getSuperclass(), fields);
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                field.trySetAccessible();
                fields.add(field);
            }
        }
    }

    private static void addMethod(Map<Signature, Method> index, Method method) {
        if (!method.isBridge() && !method.isSynthetic()) {
            index.putIfAbsent(signature(method.getName(), method.getParameterTypes()), method);
        }
    }

    private static Signature signature(String name, Class<?>[] types) {
        Class<?>[] copy = types == null ? new Class<?>[0] : types.clone();
        return new Signature(Objects.requireNonNull(name), Collections.unmodifiableList(Arrays.asList(copy)));
    }

    public static boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return value instanceof String string && string.isEmpty();
    }

    public static boolean isNotEmpty(Object value) { return !isEmpty(value); }
    public static <E> E newInstance(Class<E> type) { return type.cast(HandleAccess.construct(type)); }
    public static List<Field> getAllDeclaredFields(Class<?> type) { return METADATA.get(type).fields; }
    public static Field findField(Class<?> type, String name) { return METADATA.get(type).fieldIndex.get(name); }
    public static boolean hasField(Class type, String name) { return findField(type, name) != null; }

    /** Empty parameter types means a zero-argument method. Ambiguous matches fail explicitly. */
    public static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Metadata metadata = METADATA.get(type);
        Signature key = signature(name, parameterTypes);
        return metadata.resolved.computeIfAbsent(key, signature ->
                Optional.ofNullable(resolve(metadata.methods, signature))).orElse(null);
    }

    private static Method resolve(List<Method> methods, Signature key) {
        Class<?>[] args = key.types().toArray(Class<?>[]::new);
        List<Method> matches = new ArrayList<>();
        for (Method method : methods) {
            if (!method.getName().equals(key.name())) continue;
            Class<?>[] params = method.getParameterTypes();
            if (Arrays.equals(params, args)) return accessible(method);
            if (params.length != args.length) continue;
            boolean compatible = true;
            for (int i = 0; i < params.length; i++) compatible &= compatible(params[i], args[i]);
            if (compatible) matches.add(method);
        }
        if (matches.isEmpty()) return null;
        List<Method> best = new ArrayList<>();
        for (Method candidate : matches) {
            boolean dominated = false;
            for (Method other : matches) {
                if (other != candidate && moreSpecific(other, candidate)) { dominated = true; break; }
            }
            if (!dominated) best.add(candidate);
        }
        if (best.size() != 1) throw new IllegalArgumentException("Ambiguous method: " + key);
        return accessible(best.getFirst());
    }

    private static Method accessible(Method method) {
        method.trySetAccessible();
        return method;
    }

    private static boolean moreSpecific(Method left, Method right) {
        Class<?>[] a = left.getParameterTypes(), b = right.getParameterTypes();
        boolean strict = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b[i]) continue;
            if (!compatible(b[i], a[i])) return false;
            if (compatible(a[i], b[i])) return false;
            strict = true;
        }
        return strict;
    }

    private static boolean compatible(Class<?> target, Class<?> source) {
        if (source == null) return !target.isPrimitive();
        if (target == source || target.isAssignableFrom(source)) return true;
        if (!target.isPrimitive()) return target.isAssignableFrom(box(source));
        Class<?> primitive = unbox(source);
        if (target == primitive) return true;
        if (primitive == byte.class) return target == short.class || target == int.class || target == long.class || target == float.class || target == double.class;
        if (primitive == short.class || primitive == char.class) return target == int.class || target == long.class || target == float.class || target == double.class;
        if (primitive == int.class) return target == long.class || target == float.class || target == double.class;
        if (primitive == long.class) return target == float.class || target == double.class;
        return primitive == float.class && target == double.class;
    }

    private static Class<?> box(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == char.class) return Character.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        return type;
    }

    private static Class<?> unbox(Class<?> type) {
        for (Class<?> primitive : List.of(int.class, long.class, boolean.class, double.class, float.class, char.class, byte.class, short.class)) {
            if (box(primitive) == type) return primitive;
        }
        return type;
    }

    public static Object invokeMethod(Object bean, String name, Object... args) {
        if (bean == null || name == null) return null;
        Object[] values = args == null ? new Object[0] : args;
        Class<?>[] types = Arrays.stream(values).map(value -> value == null ? null : value.getClass()).toArray(Class<?>[]::new);
        Method method = findMethod(bean.getClass(), name, types);
        if (method == null) throw new IllegalArgumentException("Method not found: " + bean.getClass().getName() + "." + name);
        return invokeMethod(bean, method, values);
    }

    /** Ordinary virtual dispatch, including overrides; static methods accept a null receiver. */
    public static Object invokeMethod(Object bean, Method method, Object... args) {
        return HandleAccess.invoke(bean, method, args == null ? new Object[0] : args);
    }


    public static Object invokeGetter(Object bean, String path) {
        if (bean == null || path == null) return null;
        int dot = path.indexOf('.');
        if (dot >= 0) return invokeGetter(invokeGetter(bean, path.substring(0, dot)), path.substring(dot + 1));
        if (bean instanceof Map<?, ?> map) return map.get(path);
        if (bean instanceof List<?> list) return list.get(Integer.parseInt(path));
        if (bean.getClass().isArray()) return Array.get(bean, Integer.parseInt(path));
        return property(bean.getClass(), path).get(bean);
    }

    private static Method getter(Class<?> type, String name) {
        return METADATA.get(type).getters.computeIfAbsent(name,
                key -> Optional.ofNullable(resolveGetter(type, key))).orElse(null);
    }

    private static Method resolveGetter(Class<?> type, String name) {
        String suffix = suffix(name);
        Method getter = findMethod(type, "get" + suffix);
        if (getter != null && getter.getReturnType() != void.class) return getter;
        getter = findMethod(type, "is" + suffix);
        return getter != null && (getter.getReturnType() == boolean.class || getter.getReturnType() == Boolean.class) ? getter : null;
    }

    private static String suffix(String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("Property name must not be empty");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static Object invokeGetter(Object bean, Field field) {
        if (bean == null) return null;
        return property(field).get(bean);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void invokeSetter(Object bean, String path, Object value) {
        if (bean == null || path == null) return;
        int dot = path.indexOf('.');
        if (dot >= 0) { invokeSetter(invokeGetter(bean, path.substring(0, dot)), path.substring(dot + 1), value); return; }
        if (bean instanceof Map map) { map.put(path, value); return; }
        if (bean instanceof List list) {
            int index = Integer.parseInt(path);
            if (index == list.size()) list.add(value); else list.set(index, value);
            return;
        }
        if (bean.getClass().isArray()) { Array.set(bean, Integer.parseInt(path), value); return; }
        property(bean.getClass(), path).set(bean, value);
    }

    public static void invokeSetter(Object bean, Field field, Object value) {
        if (bean != null) property(field).set(bean, value);
    }

    /** Cache this accessor outside row loops; virtual getters/setters still dispatch to proxies. */
    public static PropertyAccess property(Class<?> type, String name) {
        return METADATA.get(type).properties.computeIfAbsent(name, key -> new PropertyAccess(type, key, null));
    }

    public static PropertyAccess property(Field field) {
        return METADATA.get(field.getDeclaringClass()).fieldProperties.computeIfAbsent(field,
                key -> new PropertyAccess(key.getDeclaringClass(), key.getName(), key));
    }

    public static final class PropertyAccess {
        private final Class<?> type;
        private final String name;
        private final Field field;
        private volatile MethodHandle reader;
        private final Map<Class<?>, MethodHandle> writers = new ConcurrentHashMap<>();
        private PropertyAccess(Class<?> type, String name, Field field) {
            this.type = type;
            this.name = name;
            this.field = field;
        }
        public Object get(Object bean) {
            MethodHandle handle = reader;
            if (handle == null) {
                Method method = getter(type, name);
                handle = method != null ? HandleAccess.getter(method)
                        : HandleAccess.field(field != null ? field : requiredField(type, name), false);
                reader = handle;
            }
            return HandleAccess.get(handle, bean);
        }
        public void set(Object bean, Object value) {
            MethodHandle handle = writers.computeIfAbsent(value == null ? void.class : value.getClass(), key -> {
                Method setter = findMethod(type, "set" + suffix(name), new Class<?>[]{key == void.class ? null : key});
                if (setter != null) return HandleAccess.setter(setter);
                if (METADATA.get(type).setterNames.contains("set" + suffix(name))) {
                    throw new IllegalArgumentException("No compatible setter: " + type.getName() + "." + name);
                }
                return HandleAccess.field(field != null ? field : requiredField(type, name), true);
            });
            HandleAccess.set(handle, bean, value);
        }
    }

    private static Field requiredField(Class<?> type, String name) {
        Field field = findField(type, name);
        if (field == null) throw new IllegalArgumentException("Property not found: " + type.getName() + "." + name);
        return field;
    }

    public static Object parse(Object bean, String path) {
        if (path == null || !path.startsWith("${")) return path;
        if (!path.endsWith("}") || path.length() <= 3) throw new IllegalArgumentException("Invalid property expression: " + path);
        return invokeGetter(bean, path.substring(2, path.length() - 1));
    }

    /** Value classification retained for ORM compatibility; does not imply immutability. */
    public static boolean isPrimitive(Object value) { return value == null || isPrimitiveType(value.getClass()); }
    public static boolean isPrimitiveType(Class<?> type) { return ValueCopies.isValueType(type); }
    public static <E> E clone(E value) { return clone(value, false); }
    /** Selects shallow or graph-based deep copying without JSON serialization. */
    public static <E> E clone(E value, boolean deep) {
        return value == null ? null : deep ? deepClone(value) : ValueCopies.shallow(value);
    }

    /**
     * Deep-clones beans, arrays and containers, preserving aliases and supported cycles.
     * Null returns null. Beans require a usable no-argument constructor and writable properties.
     * Records are unsupported. Collection wrappers may become mutable standard containers;
     * use interface types for those results. No unrelated bean/DTO conversion is performed.
     */
    @SuppressWarnings("unchecked")
    public static <E> E deepClone(E value) {
        return (E) new DeepCopies().copy(value);
    }

    /** Deep-copies matching properties, skipping missing source properties and null values.
     * Does not convert unrelated types; failures may leave the target partially updated.
     */
    public static <K, V> V copyProperties(K bean, V target) {
        Objects.requireNonNull(bean, "source");
        Objects.requireNonNull(target, "target");
        return DeepCopies.copyProperties(bean, target);
    }

    static void copyMatchingProperties(Object bean, Object target, DeepCopies context) {
        for (Field field : getAllDeclaredFields(target.getClass())) {
            if (field.isSynthetic()) continue;
            if (!(bean instanceof Map<?, ?>) && findField(bean.getClass(), field.getName()) == null
                    && getter(bean.getClass(), field.getName()) == null) continue;
            Object value = invokeGetter(bean, field.getName());
            if (value != null) invokeSetter(target, field, context.copy(value));
        }
    }
}
