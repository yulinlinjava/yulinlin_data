package com.yulinlin.regression.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.core.cache.*;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅源码回归：不启动 Spring、不测试 YAML 绑定、不连接 Redis/磁盘。 */
class CacheRegistryTest {
    @Test void noProviderKeepsNoOpButExplicitUnknownNameFails() {
        CacheRegistry registry = new CacheRegistry(List.of(), null);
        assertSame(NoOpQueryCache.INSTANCE, registry.defaultCache());
        CacheClient client = new RoutingCacheClient(registry);
        Cache cache = client.getCache();
        assertFalse(cache.available());
        assertNull(client.defaultName());
        assertTrue(client.names().isEmpty());
        assertTrue(client.caches().isEmpty());
        assertFalse(client.contains("redis"));
        cache.set("user", "1", "value");
        assertNull(cache.get("user", "1", String.class));
        assertEquals("loaded", cache.getOrLoad("user", "1", String.class, () -> "loaded"));
        cache.clear("user");
        cache.clear();
        assertThrows(IllegalArgumentException.class, () -> client.getCache("redis"));
        assertThrows(IllegalArgumentException.class, () -> new CacheRegistry(List.of(), "redis"));
    }

    @Test void singleProviderNeedsNoDefaultAndMultipleProvidersRequireOne() {
        QueryCache local = provider("caffeine"), remote = provider("redis");
        assertSame(local, new CacheRegistry(List.of(local), null).defaultCache());
        assertSame(remote, new CacheRegistry(List.of(local, remote), "redis").defaultCache());
        assertThrows(IllegalStateException.class, () -> new CacheRegistry(List.of(local, remote), null));
        assertThrows(IllegalArgumentException.class, () -> new CacheRegistry(List.of(local), "redis"));
        assertThrows(IllegalArgumentException.class, () -> new CacheRegistry(List.of(local), ""));
        assertThrows(IllegalStateException.class,
                () -> new CacheRegistry(List.of(local, provider("CAFFEINE")), "caffeine"));
    }

    @Test void configurationExposesDefaultJavaBeanPropertyWithoutAGroupField() throws Exception {
        QueryCacheProperties properties = new QueryCacheProperties();
        var property = Arrays.stream(Introspector.getBeanInfo(QueryCacheProperties.class).getPropertyDescriptors())
                .filter(item -> item.getName().equals("default")).findFirst().orElseThrow();
        property.getWriteMethod().invoke(properties, "redis");
        assertEquals("redis", property.getReadMethod().invoke(properties));
        assertThrows(IllegalArgumentException.class, () -> properties.setDefault(" "));
    }

    @Test void defaultAndNamedCachesAreReusedAndManagementViewsAreReadOnly() {
        QueryCache local = provider("caffeine"), disk = provider("ehcache"), remote = provider("redis");
        CacheClient client = new RoutingCacheClient(new CacheRegistry(List.of(local, disk, remote), "redis"));
        assertEquals("redis", client.defaultName());
        assertSame(client.getCache(), client.getCache("redis"));
        assertSame(client.getCache("caffeine"), client.getCache("caffeine"));
        assertEquals("ehcache", client.getCache("ehcache").name());
        assertTrue(client.contains(" CAFFEINE "));
        assertFalse(client.contains(null));
        assertFalse(client.contains(" "));
        assertEquals(Set.of("caffeine", "ehcache", "redis"), client.names());
        assertSame(client.getCache("redis"), client.caches().get("redis"));
        assertThrows(UnsupportedOperationException.class, () -> client.names().remove("redis"));
        assertThrows(UnsupportedOperationException.class, () -> client.caches().clear());

        client.getCache().set("user", "1", "default");
        client.getCache().set("user", "old-string-with-ttl", "value", Duration.ofSeconds(10));
        client.getCache().set("user", "old-duration-value", Duration.ofMinutes(2), null);
        client.getCache("caffeine").set("user", "1", "local");
        client.getCache("ehcache").set("user", "1", "disk");
        verify(remote).putApplication("user", "1", "default", null);
        verify(remote).putApplication("user", "old-string-with-ttl", "value", Duration.ofSeconds(10));
        verify(remote).putApplication("user", "old-duration-value", Duration.ofMinutes(2), null);
        verify(local).putApplication("user", "1", "local", null);
        verify(disk).putApplication("user", "1", "disk", null);

        client.getCache().get("user", "1", String.class);
        client.getCache("caffeine").get("user", "1", String.class);
        TypeReference<List<String>> type = new TypeReference<>() {};
        client.getCache("ehcache").get("user", "1", type);
        verify(remote).getApplication("user", "1", String.class);
        verify(local).getApplication("user", "1", String.class);
        verify(disk).getApplication("user", "1", type.getType());

        client.getCache("caffeine").getOrLoad("user", "loaded", String.class, Duration.ofSeconds(20), () -> "data");
        verify(local).putApplication("user", "loaded", "data", Duration.ofSeconds(20));
        assertThrows(IllegalArgumentException.class, () -> client.getCache("unknown"));
    }

    @Test void applicationClearingNeverBroadcastsOrClearsOrmEntries() {
        QueryCache local = provider("caffeine"), remote = provider("redis");
        CacheRegistry registry = new CacheRegistry(List.of(local, remote), "redis");
        CacheClient client = new RoutingCacheClient(registry);
        client.getCache().clear();
        client.getCache("caffeine").clear("user");
        client.getCache("caffeine").exists("user", "1");
        client.getCache("caffeine").remove("user", "1");
        verify(remote).clearApplication();
        verify(local, never()).clearApplication();
        verify(local).invalidateApplication("user");
        verify(remote, never()).invalidateApplication(anyString());
        verify(local).containsApplication("user", "1");
        verify(local).removeApplication("user", "1");
        verify(local, never()).invalidateAll();
        verify(remote, never()).invalidateAll();

        // ORM 只持有默认实例；不经过客户端，也不广播到其他实现。
        registry.defaultCache().invalidateAll();
        verify(remote).invalidateAll();
        verify(local, never()).invalidateAll();
        client.getCache("caffeine").clear();
        verify(local).clearApplication();
        verify(remote, times(1)).clearApplication();
    }

    @Test void genericLoadingAndBatchOperationsReuseApplicationProtocol() {
        QueryCache provider = provider("caffeine");
        Cache cache = new RoutingCacheClient(new CacheRegistry(List.of(provider), null)).getCache();
        TypeReference<List<String>> type = new TypeReference<>() {};
        assertEquals(List.of("a"), cache.getOrLoad("list", "1", type,
                Duration.ofSeconds(5), () -> List.of("a")));
        verify(provider).putApplication("list", "1", List.of("a"), Duration.ofSeconds(5));
        assertNull(cache.getOrLoad("list", "empty", type, () -> null));
        verify(provider, never()).putApplication(eq("list"), eq("empty"), any(), any());
        when(provider.getApplication("list", "hit", type.getType()))
                .thenReturn(CacheLookup.hit(List.of("cached")));
        assertEquals(List.of("cached"), cache.getOrLoad("list", "hit", type,
                () -> { throw new AssertionError("命中后不应回源"); }));

        cache.setAll("user", Map.of("1", "a", "2", "b"));
        verify(provider).putApplication("user", "1", "a", null);
        verify(provider).putApplication("user", "2", "b", null);
        cache.setAll("user", Map.of("3", "c"), Duration.ofMinutes(1));
        verify(provider).putApplication("user", "3", "c", Duration.ofMinutes(1));
        when(provider.getApplication("user", "1", String.class)).thenReturn(CacheLookup.hit("a"));
        assertEquals(Map.of("1", "a"), cache.getAll("user", List.of("1", "missing"), String.class));
        cache.removeAll("user", List.of("1", "2"));
        verify(provider).removeApplication("user", "1");
        verify(provider).removeApplication("user", "2");
        verify(provider, never()).invalidateAll();
    }

    private static QueryCache provider(String name) {
        QueryCache provider = mock(QueryCache.class);
        when(provider.providerName()).thenReturn(name);
        when(provider.available()).thenReturn(true);
        when(provider.getApplication(anyString(), anyString(), any())).thenReturn(CacheLookup.miss());
        return provider;
    }
}
