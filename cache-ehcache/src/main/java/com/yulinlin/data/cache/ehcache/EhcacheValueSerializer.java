package com.yulinlin.data.cache.ehcache;

/** Value formats supported by the Ehcache heap/off-heap/disk tiers. */
public enum EhcacheValueSerializer {
    /** Text JSON with the application's Jackson configuration. */
    JSON,
    /** Compact binary JSON with the application's Jackson configuration. */
    SMILE,
    /** High-throughput binary object graph serialization for trusted cache storage. */
    FORY
}
