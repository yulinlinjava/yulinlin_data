package com.yulinlin.data.core.cache;

/** Distinguishes a cached null from a cache miss. */
public record CacheLookup(boolean hit, Object value) {
    private static final CacheLookup MISS = new CacheLookup(false, null);
    public static CacheLookup miss() { return MISS; }
    public static CacheLookup hit(Object value) { return new CacheLookup(true, value); }
}
