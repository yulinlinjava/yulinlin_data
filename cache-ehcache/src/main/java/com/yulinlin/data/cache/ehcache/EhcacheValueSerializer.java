package com.yulinlin.data.cache.ehcache;

/** Value formats supported by the Ehcache heap/off-heap/disk tiers. */
public enum EhcacheValueSerializer {
    /** Text JSON with the application's Jackson configuration. */
    JSON,
    /** Compact binary JSON with the application's Jackson configuration. */
    SMILE,
    /** Compact binary object graph serialization backed by a virtual-thread-friendly object pool. */
    KRYO
}
