package com.yulinlin.data.core.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.function.Supplier;

/** Public application-cache API backed by the selected optional cache provider. */
public interface CacheClient {
    boolean available();
    String providerName();
    <T> T get(String namespace, String key, Class<T> type);
    <T> T get(String namespace, String key, TypeReference<T> type);
    void set(String namespace, String key, Object value);
    void set(String namespace, String key, Object value, Duration ttl);
    boolean exists(String namespace, String key);
    boolean remove(String namespace, String key);
    void invalidate(String namespace);
    void clear();
    <T> T getOrLoad(String namespace, String key, Class<T> type, Supplier<T> loader);
    <T> T getOrLoad(String namespace, String key, Class<T> type, Duration ttl, Supplier<T> loader);
}
