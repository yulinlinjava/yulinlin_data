package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseType;

import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.time.Duration;

/** Stable, database-independent key for one logical query. */
public final class CacheKey {

    private static final String VERSION = "v2";
    private static final HexFormat HEX = HexFormat.of();

    private final long high;
    private final long low;
    private volatile String value;

    private CacheKey(long high, long low) {
        this.high = high;
        this.low = low;
    }

    public static CacheKey query(String group,
                                 Object cluster,
                                 Class<?> sessionType,
                                 Class<?> entityClass,
                                 Class<?> fromClass,
                                 ParseType parseType,
                                 INode node) {
        Murmur3Hash128 hasher = new Murmur3Hash128();
        CacheKeyMetadata.write(hasher, VERSION);
        CacheKeyMetadata.write(hasher, group);
        CacheKeyMetadata.write(hasher, cluster);
        CacheKeyMetadata.write(hasher, sessionType);
        CacheKeyMetadata.write(hasher, entityClass);
        CacheKeyMetadata.write(hasher, fromClass);
        CacheKeyMetadata.write(hasher, parseType);
        CacheKeyMetadata.write(hasher, node);
        Murmur3Hash128.Result hash = hasher.finish();
        return new CacheKey(hash.first(), hash.second());
    }

    /** Derives a physical cache key from a logical query and a stable namespace-version snapshot. */
    public CacheKey scoped(long globalVersion, Map<CacheNamespace, Long> namespaceVersions) {
        return scoped(globalVersion, namespaceVersions, null);
    }

    /** Derives a physical key; resolved TTL is part of the identity to keep per-query policies independent. */
    public CacheKey scoped(long globalVersion,
                           Map<CacheNamespace, Long> namespaceVersions,
                           Duration ttl) {
        Murmur3Hash128 hasher = new Murmur3Hash128();
        hasher.putLong(high);
        hasher.putLong(low);
        hasher.putLong(globalVersion);
        if (ttl == null) {
            hasher.putLong(Long.MIN_VALUE);
        } else {
            hasher.putLong(ttl.getSeconds());
            hasher.putLong(ttl.getNano());
        }
        namespaceVersions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> {
                    CacheKeyMetadata.write(hasher, entry.getKey().value());
                    hasher.putLong(entry.getValue());
                });
        Murmur3Hash128.Result hash = hasher.finish();
        return new CacheKey(hash.first(), hash.second());
    }

    @Deprecated
    public static CacheKey of(ParseType type, INode node) {
        return query(null, null, null, null, null, type, node);
    }

    @Deprecated
    public static CacheKey of(ParseType type, INode node, String namespace) {
        return query(namespace, null, null, null, null, type, node);
    }

    public String value() {
        String current = value;
        if (current == null) {
            current = VERSION + ":" + HEX.toHexDigits(high) + HEX.toHexDigits(low);
            value = current;
        }
        return current;
    }

    /** @deprecated A 32-bit key is not collision-safe. Use {@link #value()}. */
    @Deprecated
    public int getKey() {
        return hashCode();
    }

    /** @deprecated Cache invalidation is now TTL-only. */
    @Deprecated
    public boolean isSingleEqualsCondition() {
        return false;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof CacheKey other && high == other.high && low == other.low;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(high) + Long.hashCode(low);
    }

    @Override
    public String toString() {
        return value();
    }
}
