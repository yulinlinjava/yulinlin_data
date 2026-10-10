package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.repository.anno.JoinCache;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Parsed once per Repository method; absence of the annotation always means no cache. */
record JoinCacheOptions(boolean enabled, CacheMode mode, Duration ttl, List<String> namespaces) {

    private static final JoinCacheOptions DISABLED =
            new JoinCacheOptions(false, CacheMode.NONE, null, List.of());

    static JoinCacheOptions from(Method method) {
        JoinCache annotation = method.getAnnotation(JoinCache.class);
        if (annotation == null) return DISABLED;

        long amount = annotation.ttl();
        if (amount == 0 || amount < -1) {
            throw invalid(method, "ttl must be -1 or positive");
        }
        Duration ttl;
        try {
            ttl = amount == -1 ? null : Duration.of(amount, annotation.unit().toChronoUnit());
        } catch (RuntimeException error) {
            throw invalid(method, "ttl is too large", error);
        }

        List<String> namespaces = Arrays.stream(annotation.namespaces())
                .map(namespace -> {
                    if (namespace == null || namespace.isBlank()) {
                        throw invalid(method, "namespace must not be blank");
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

    private static IllegalArgumentException invalid(Method method, String message) {
        return new IllegalArgumentException("Invalid @JoinCache on " + method.toGenericString() + ": " + message);
    }

    private static IllegalArgumentException invalid(Method method, String message, Throwable cause) {
        return new IllegalArgumentException("Invalid @JoinCache on " + method.toGenericString() + ": " + message,
                cause);
    }
}
