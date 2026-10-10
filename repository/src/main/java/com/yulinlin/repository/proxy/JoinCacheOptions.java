package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.data.core.anno.JoinCache;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parsed once per Repository type and method. Method configuration outranks interface defaults. */
record JoinCacheOptions(boolean enabled, CacheMode mode, Duration ttl, List<String> namespaces) {

    private static final JoinCacheOptions DISABLED =
            new JoinCacheOptions(false, CacheMode.NONE, null, List.of());

    static JoinCacheOptions from(Class<?> repositoryType, Method method) {
        JoinCache annotation = findMethodAnnotation(repositoryType, method);
        String location = method.toGenericString();
        if (annotation == null) {
            annotation = findTypeAnnotation(repositoryType, new HashSet<>());
            location = repositoryType.getName();
        }
        if (annotation == null) return DISABLED;

        return parse(annotation, location);
    }

    static void validateRepository(Class<?> repositoryType) {
        JoinCache annotation = findTypeAnnotation(repositoryType, new HashSet<>());
        if (annotation != null) parse(annotation, repositoryType.getName());
    }

    static boolean hasMethodAnnotation(Class<?> repositoryType, Method method) {
        return findMethodAnnotation(repositoryType, method) != null;
    }

    private static JoinCacheOptions parse(JoinCache annotation, String location) {

        long amount = annotation.ttl();
        if (amount == 0 || amount < -1) {
            throw invalid(location, "ttl must be -1 or positive");
        }
        Duration ttl;
        try {
            ttl = amount == -1 ? null : Duration.of(amount, annotation.unit().toChronoUnit());
        } catch (RuntimeException error) {
            throw invalid(location, "ttl is too large", error);
        }

        List<String> namespaces = Arrays.stream(annotation.namespaces())
                .map(namespace -> {
                    if (namespace == null || namespace.isBlank()) {
                        throw invalid(location, "namespace must not be blank");
                    }
                    return namespace.trim();
                })
                .distinct()
                .toList();
        return new JoinCacheOptions(true, annotation.mode(), ttl, namespaces);
    }

    @SuppressWarnings("rawtypes")
    void apply(BaseModelSelectWrapper wrapper) {
        if (!enabled) return;
        if (ttl == null) wrapper.cache(mode);
        else wrapper.cache(mode, ttl);
        if (!namespaces.isEmpty()) wrapper.cacheNamespaces(namespaces.toArray(String[]::new));
    }

    private static JoinCache findMethodAnnotation(Class<?> repositoryType, Method method) {
        return findMethodAnnotation(repositoryType, method.getName(), method.getParameterTypes(), new HashSet<>());
    }

    private static JoinCache findMethodAnnotation(Class<?> type, String name,
                                                  Class<?>[] parameterTypes, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return null;
        try {
            JoinCache annotation = type.getDeclaredMethod(name, parameterTypes).getDeclaredAnnotation(JoinCache.class);
            if (annotation != null) return annotation;
        } catch (NoSuchMethodException ignored) {
            // Continue through parent interfaces.
        }
        for (Class<?> parent : type.getInterfaces()) {
            JoinCache annotation = findMethodAnnotation(parent, name, parameterTypes, visited);
            if (annotation != null) return annotation;
        }
        return null;
    }

    private static JoinCache findTypeAnnotation(Class<?> type, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return null;
        JoinCache annotation = type.getDeclaredAnnotation(JoinCache.class);
        if (annotation != null) return annotation;
        for (Class<?> parent : type.getInterfaces()) {
            annotation = findTypeAnnotation(parent, visited);
            if (annotation != null) return annotation;
        }
        return null;
    }

    private static IllegalArgumentException invalid(String location, String message) {
        return new IllegalArgumentException("Invalid @JoinCache on " + location + ": " + message);
    }

    private static IllegalArgumentException invalid(String location, String message, Throwable cause) {
        return new IllegalArgumentException("Invalid @JoinCache on " + location + ": " + message, cause);
    }
}
