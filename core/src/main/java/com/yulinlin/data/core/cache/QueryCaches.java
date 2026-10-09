package com.yulinlin.data.core.cache;

import java.util.List;

/** Resolves the optional provider and rejects ambiguous classpaths. */
public final class QueryCaches {

    private QueryCaches() {
    }

    public static QueryCache single(List<QueryCache> providers) {
        if (providers == null || providers.isEmpty()) return NoOpQueryCache.INSTANCE;
        if (providers.size() > 1) {
            throw new IllegalStateException("Multiple query-cache providers found: "
                    + providers.stream().map(QueryCache::providerName).toList()
                    + ". Keep only cache-caffeine or cache-ehcache.");
        }
        return providers.getFirst();
    }
}
