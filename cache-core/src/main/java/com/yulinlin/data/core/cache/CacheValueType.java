package com.yulinlin.data.core.cache;

import java.util.Objects;

/** Type information required to restore a persistent cache value. */
public record CacheValueType(Class<?> valueClass, boolean collection) {
    public CacheValueType { valueClass = Objects.requireNonNullElse(valueClass, Object.class); }
    public static CacheValueType scalar(Class<?> valueClass) { return new CacheValueType(valueClass, false); }
    public static CacheValueType listOf(Class<?> elementClass) { return new CacheValueType(elementClass, true); }
}
