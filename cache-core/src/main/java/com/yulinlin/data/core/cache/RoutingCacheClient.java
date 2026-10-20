package com.yulinlin.data.core.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 每个实现复用一个缓存对象及其加载锁；不增加缓存层，不接管 Provider 的销毁。 */
public final class RoutingCacheClient implements CacheClient {
    private final Map<String, Cache> clients;
    private final Cache defaults;
    private final String defaultName;

    public RoutingCacheClient(CacheRegistry registry) {
        Map<String, Cache> built = new LinkedHashMap<>();
        registry.providers().forEach((name, provider) -> built.put(name, new DefaultCache(provider)));
        clients = Collections.unmodifiableMap(built);
        defaultName = clients.isEmpty() ? null : CacheRegistry.name(registry.defaultCache().providerName());
        defaults = defaultName == null ? new DefaultCache(NoOpQueryCache.INSTANCE) : clients.get(defaultName);
    }

    @Override public Cache getCache(String name) {
        Cache client = clients.get(CacheRegistry.name(name));
        if (client == null) throw new IllegalArgumentException("缓存不存在：" + name + "，已注册：" + clients.keySet());
        return client;
    }
    @Override public Cache getCache() { return defaults; }
    @Override public String defaultName() { return defaultName; }
    @Override public boolean contains(String name) {
        return name != null && !name.isBlank() && clients.containsKey(CacheRegistry.name(name));
    }
    @Override public Set<String> names() { return clients.keySet(); }
    @Override public Map<String, Cache> caches() { return clients; }
}
