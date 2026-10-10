package com.yulinlin.data.core.cache;

/** Controls how one query interacts with the optional cache provider. */
public enum CacheMode {
    /** Query the data source without reading or writing cache. */
    NONE,
    /** Read cache only; a miss never falls back to the data source. */
    CACHE_ONLY,
    /** Read cache first, then load and cache on a miss. */
    READ_THROUGH,
    /** Always load from the data source and overwrite the cache entry. */
    REFRESH
}
