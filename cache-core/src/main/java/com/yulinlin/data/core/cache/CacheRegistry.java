package com.yulinlin.data.core.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 初始化后固定的缓存注册表；ORM 只取默认实例，客户端可按名称选择。 */
public final class CacheRegistry {
    private final Map<String, QueryCache> providers;
    private final QueryCache defaultCache;

    public CacheRegistry(List<QueryCache> caches, String configuredDefault) {
        Map<String, QueryCache> registered = new LinkedHashMap<>();
        if (caches != null) for (QueryCache cache : caches) {
            Objects.requireNonNull(cache, "缓存实现不能为空");
            String name = name(cache.providerName());
            if (registered.putIfAbsent(name, cache) != null)
                throw new IllegalStateException("缓存名称重复：" + name);
        }
        providers = Collections.unmodifiableMap(registered);
        if (configuredDefault != null) {
            defaultCache = required(configuredDefault);
        } else if (providers.isEmpty()) {
            defaultCache = NoOpQueryCache.INSTANCE;
        } else if (providers.size() == 1) {
            defaultCache = providers.values().iterator().next();
        } else {
            throw new IllegalStateException("存在多个缓存实现 " + providers.keySet()
                    + "，请配置 yulinlin.cache.default");
        }
    }

    public QueryCache defaultCache() { return defaultCache; }
    public Map<String, QueryCache> providers() { return providers; }

    public QueryCache required(String name) {
        QueryCache cache = providers.get(name(name));
        if (cache == null)
            throw new IllegalArgumentException("缓存不存在：" + name + "，已注册：" + providers.keySet());
        return cache;
    }

    static String name(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("缓存名称不能为空");
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
