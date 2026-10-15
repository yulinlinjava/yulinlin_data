package com.yulinlin.data.cache.redis;

/** Wire formats supported by the Redis L2 value codec. */
public enum RedisValueSerializer {
    /** Compact binary JSON while retaining the application's Jackson configuration. */
    SMILE,
    /** Text JSON, mainly useful while inspecting cache values. */
    JSON,
    /** High-throughput binary object graph serialization for trusted cache storage. */
    FORY
}
