package com.yulinlin.data.lang.reflection;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves generic types through Spring's {@link ResolvableType} and caches the final result.
 * Cache ownership follows the declaring/source class through {@link ClassValue}, so unloading an
 * application class also releases all generic metadata cached for it.
 */
public final class GenericUtil {

    private static final Resolution UNRESOLVED = new Resolution(null);
    private static volatile ClassValue<CacheBucket> cache = newCache();

    private GenericUtil() {
    }

    /**
     * Resolves a generic parameter declared by a parent class or interface.
     *
     * @param from concrete child class
     * @param to generic parent class or interface
     * @param index nested generic indexes
     * @return resolved class, or {@code null} when the type is not concrete
     */
    public static Class<?> getGeneric(Class<?> from, Class<?> to, int... index) {
        return forClass(from, to, index);
    }

    /** Allocation-free common path for one generic index. */
    public static Class<?> getGeneric(Class<?> from, Class<?> to, int index) {
        return resolveClass(from, to, PathKey.single(index));
    }

    public static Class<?> forClass(Class<?> from, Class<?> to, int... index) {
        return resolveClass(from, to, PathKey.of(index));
    }

    /** Allocation-free common path for one generic index. */
    public static Class<?> forClass(Class<?> from, Class<?> to, int index) {
        return resolveClass(from, to, PathKey.single(index));
    }

    private static Class<?> resolveClass(Class<?> from, Class<?> to, PathKey path) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        PathCache values = cache.get(from).classTypes.computeIfAbsent(to, ignored -> new PathCache());
        Resolution cached = values.get(path);
        if (cached != null) return cached.type();

        Class<?> resolved = ResolvableType.forClass(from).as(to)
                .getGeneric(path.indexes())
                .resolve();
        return values.putIfAbsent(path, resolved).type();
    }

    public static Class<?> forField(Field field, int... index) {
        return resolveField(field, PathKey.of(index));
    }

    /** Allocation-free common path for one generic index. */
    public static Class<?> forField(Field field, int index) {
        return resolveField(field, PathKey.single(index));
    }

    private static Class<?> resolveField(Field field, PathKey path) {
        Objects.requireNonNull(field, "field");
        PathCache values = cache.get(field.getDeclaringClass()).fields
                .computeIfAbsent(field, ignored -> new PathCache());
        Resolution cached = values.get(path);
        if (cached != null) return cached.type();

        Class<?> resolved = ResolvableType.forField(field)
                .getGeneric(path.indexes())
                .resolve();
        return values.putIfAbsent(path, resolved).type();
    }

    public static Class<?> getFieldGeneric(Field field, int... index) {
        return forField(field, index);
    }

    /** Allocation-free common path for one generic index. */
    public static Class<?> getFieldGeneric(Field field, int index) {
        return resolveField(field, PathKey.single(index));
    }

    /** Resolves a method parameter's nested generic type. */
    public static Class<?> forMethodParameter(Method method, int parameterIndex, int... keys) {
        return resolveMethodParameter(method, parameterIndex, PathKey.of(keys));
    }

    /** Allocation-free common path for the first generic parameter. */
    public static Class<?> forMethodParameter(Method method, int parameterIndex) {
        return resolveMethodParameter(method, parameterIndex, PathKey.empty());
    }

    /** Allocation-free common path for one generic index. */
    public static Class<?> forMethodParameter(Method method, int parameterIndex, int genericIndex) {
        return resolveMethodParameter(method, parameterIndex, PathKey.single(genericIndex));
    }

    private static Class<?> resolveMethodParameter(Method method, int parameterIndex, PathKey path) {
        Objects.requireNonNull(method, "method");
        PathCache values = cache.get(method.getDeclaringClass()).methodParameters
                .computeIfAbsent(method, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(parameterIndex, ignored -> new PathCache());
        Resolution cached = values.get(path);
        if (cached != null) return cached.type();

        MethodParameter parameter = new MethodParameter(method, parameterIndex);
        Class<?> resolved = ResolvableType.forMethodParameter(parameter)
                .getGeneric(path.indexes())
                .resolve();
        return values.putIfAbsent(path, resolved).type();
    }

    public static Class<?> forMethodReturnType(Method method, int... index) {
        return resolveMethodReturnType(method, PathKey.of(index));
    }

    /** Allocation-free common path for one generic index. */
    public static Class<?> forMethodReturnType(Method method, int index) {
        return resolveMethodReturnType(method, PathKey.single(index));
    }

    private static Class<?> resolveMethodReturnType(Method method, PathKey path) {
        Objects.requireNonNull(method, "method");
        PathCache values = cache.get(method.getDeclaringClass()).methodReturns
                .computeIfAbsent(method, ignored -> new PathCache());
        Resolution cached = values.get(path);
        if (cached != null) return cached.type();

        Class<?> resolved = ResolvableType.forMethodReturnType(method)
                .getGeneric(path.indexes())
                .resolve();
        return values.putIfAbsent(path, resolved).type();
    }

    /** Clears cached generic metadata owned by one class. */
    public static void clearCache(Class<?> owner) {
        cache.remove(Objects.requireNonNull(owner, "owner"));
    }

    /** Clears all GenericUtil caches without retaining the previously seen classes. */
    public static void clearCache() {
        cache = newCache();
    }

    private static ClassValue<CacheBucket> newCache() {
        return new ClassValue<>() {
            @Override
            protected CacheBucket computeValue(Class<?> type) {
                return new CacheBucket();
            }
        };
    }

    private static final class CacheBucket {
        private final ConcurrentMap<Class<?>, PathCache> classTypes = new ConcurrentHashMap<>();
        private final ConcurrentMap<Field, PathCache> fields = new ConcurrentHashMap<>();
        private final ConcurrentMap<Method, ConcurrentMap<Integer, PathCache>> methodParameters =
                new ConcurrentHashMap<>();
        private final ConcurrentMap<Method, PathCache> methodReturns = new ConcurrentHashMap<>();
    }

    private static final class PathCache {
        private final ConcurrentMap<PathKey, Resolution> values = new ConcurrentHashMap<>();

        private Resolution get(PathKey path) {
            return values.get(path);
        }

        private Resolution putIfAbsent(PathKey path, Class<?> type) {
            Resolution resolved = type == null ? UNRESOLVED : new Resolution(type);
            Resolution existing = values.putIfAbsent(path, resolved);
            return existing == null ? resolved : existing;
        }
    }

    private record Resolution(Class<?> type) {
    }

    /** Immutable generic-index path with shared instances for the overwhelmingly common paths. */
    private static final class PathKey {
        private static final int SINGLE_SIZE = 8;
        private static final int PAIR_SIZE = 4;
        private static final PathKey EMPTY = new PathKey(new int[0], false);
        private static final PathKey[] SINGLE = createSingles();
        private static final PathKey[][] PAIR = createPairs();

        private final int[] indexes;
        private final int hash;

        private PathKey(int[] indexes, boolean copy) {
            this.indexes = copy ? indexes.clone() : indexes;
            this.hash = Arrays.hashCode(this.indexes);
        }

        private static PathKey of(int[] indexes) {
            Objects.requireNonNull(indexes, "index");
            if (indexes.length == 0) return EMPTY;
            if (indexes.length == 1) return single(indexes[0]);
            if (indexes.length == 2
                    && indexes[0] >= 0 && indexes[0] < PAIR_SIZE
                    && indexes[1] >= 0 && indexes[1] < PAIR_SIZE) {
                return PAIR[indexes[0]][indexes[1]];
            }
            return new PathKey(indexes, true);
        }

        private static PathKey empty() {
            return EMPTY;
        }

        private static PathKey single(int index) {
            return index >= 0 && index < SINGLE_SIZE
                    ? SINGLE[index]
                    : new PathKey(new int[]{index}, false);
        }

        private int[] indexes() {
            return indexes;
        }

        @Override
        public boolean equals(Object value) {
            return this == value
                    || value instanceof PathKey other && Arrays.equals(indexes, other.indexes);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private static PathKey[] createSingles() {
            PathKey[] values = new PathKey[SINGLE_SIZE];
            for (int i = 0; i < values.length; i++) values[i] = new PathKey(new int[]{i}, false);
            return values;
        }

        private static PathKey[][] createPairs() {
            PathKey[][] values = new PathKey[PAIR_SIZE][PAIR_SIZE];
            for (int first = 0; first < PAIR_SIZE; first++) {
                for (int second = 0; second < PAIR_SIZE; second++) {
                    values[first][second] = new PathKey(new int[]{first, second}, false);
                }
            }
            return values;
        }
    }
}
