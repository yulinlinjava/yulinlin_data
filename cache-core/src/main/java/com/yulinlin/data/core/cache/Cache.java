package com.yulinlin.data.core.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** 独立于 ORM 查询缓存的业务缓存；批量操作不保证原子性，失败前可能已有部分生效。 */
public interface Cache {
    String name();
    boolean available();

    /** 未命中返回 null。 */
    <T> T get(String namespace, String key, Class<T> type);
    <T> T get(String namespace, String key, TypeReference<T> type);

    /** 不传 ttl 或传 null 使用配置默认有效期。 */
    void set(String namespace, String key, Object value);
    void set(String namespace, String key, Object value, Duration ttl);

    <T> T getOrLoad(String namespace, String key, Class<T> type, Supplier<T> loader);
    <T> T getOrLoad(String namespace, String key, Class<T> type, Duration ttl, Supplier<T> loader);
    <T> T getOrLoad(String namespace, String key, TypeReference<T> type, Supplier<T> loader);
    <T> T getOrLoad(String namespace, String key, TypeReference<T> type, Duration ttl, Supplier<T> loader);

    /** 仅返回命中的条目；默认逐条读取，不保证同一时刻的快照。 */
    default <T> Map<String, T> getAll(String namespace, Collection<String> keys, Class<T> type) {
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(type, "type");
        Map<String, T> values = new LinkedHashMap<>();
        for (String key : keys) {
            T value = get(namespace, key, type);
            if (value != null) values.put(key, value);
        }
        return values;
    }

    default void setAll(String namespace, Map<String, ?> values) {
        setAll(namespace, values, null);
    }

    /** 默认逐条写入，可由实现覆盖为批量操作。 */
    default void setAll(String namespace, Map<String, ?> values, Duration ttl) {
        Objects.requireNonNull(values, "values");
        values.forEach((key, value) -> set(namespace, key, value, ttl));
    }

    boolean exists(String namespace, String key);
    boolean remove(String namespace, String key);

    default void removeAll(String namespace, Collection<String> keys) {
        Objects.requireNonNull(keys, "keys");
        keys.forEach(key -> remove(namespace, key));
    }

    /** 仅清理当前实现的指定业务命名空间。 */
    void clear(String namespace);
    /** 仅清理当前实现的业务缓存，不清理 ORM 缓存，不清空 Redis 数据库。 */
    void clear();
}
