package com.yulinlin.data.core.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Common settings shared by all optional query-cache providers. */
@ConfigurationProperties("yulinlin.cache")
public class QueryCacheProperties {

    private String defaultCache;

    /** 对应 yulinlin.cache.default；省略时仅允许零个或一个缓存实现。 */
    public String getDefault() { return defaultCache; }

    public void setDefault(String name) { defaultCache = CacheRegistry.name(name); }

    /** Queries always expire; reads never extend this duration. */
    private Duration ttl = Duration.ofMinutes(10);

    /** Per-table ORM TTL overrides. Keys may be table or group.table. */
    private Map<String, Duration> tableTtl = new LinkedHashMap<>();

    /** Whether the resolved query TTL contributes to the physical cache key. */
    private boolean ttlInKey = false;

    /** Whether successful writes clear related ORM query-cache namespaces by default. */
    private boolean clearOnWrite = true;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        requirePositive(ttl, "yulinlin.cache.ttl");
        this.ttl = ttl;
    }

    public Map<String, Duration> getTableTtl() {
        return tableTtl;
    }

    public void setTableTtl(Map<String, Duration> tableTtl) {
        LinkedHashMap<String, Duration> normalized = new LinkedHashMap<>();
        if (tableTtl != null) {
            tableTtl.forEach((name, value) -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("yulinlin.cache.table-ttl key must not be blank");
                }
                if (value == null || value.isZero() || value.isNegative()) {
                    throw new IllegalArgumentException("yulinlin.cache.table-ttl." + name
                            + " must be positive");
                }
                normalized.put(normalize(name), value);
            });
        }
        this.tableTtl = normalized;
    }

    public boolean isTtlInKey() {
        return ttlInKey;
    }

    public void setTtlInKey(boolean ttlInKey) {
        this.ttlInKey = ttlInKey;
    }

    public boolean isClearOnWrite() {
        return clearOnWrite;
    }

    public void setClearOnWrite(boolean clearOnWrite) {
        this.clearOnWrite = clearOnWrite;
    }

    public Duration resolveTtl(Duration requested) {
        if (requested == null) return ttl;
        return requirePositive(requested, "cache ttl");
    }

    /**
     * Resolves one ORM query TTL. An explicit request always wins; otherwise every referenced table contributes
     * its group.table, table, or global default and a multi-table query uses the shortest duration.
     */
    public Duration resolveTtl(Duration requested, Set<CacheNamespace> namespaces) {
        if (requested != null) return requirePositive(requested, "cache ttl");
        if (namespaces == null || namespaces.isEmpty()) return ttl;

        Duration resolved = null;
        for (CacheNamespace namespace : namespaces) {
            String resource = normalize(namespace.resource());
            Duration current = tableTtl.get(normalize(namespace.group()) + "." + resource);
            if (current == null) current = tableTtl.get(resource);
            if (current == null) current = ttl;
            if (resolved == null || current.compareTo(resolved) < 0) resolved = current;
        }
        return resolved == null ? ttl : resolved;
    }

    /** Resolves and validates TTL, then returns the portion used by the cache-key policy. */
    public Duration resolveKeyTtl(Duration requested) {
        Duration resolved = resolveTtl(requested);
        return ttlInKey ? resolved : null;
    }

    public static Duration requirePositive(Duration ttl, String name) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return ttl;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
