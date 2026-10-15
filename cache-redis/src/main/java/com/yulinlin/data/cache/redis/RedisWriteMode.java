package com.yulinlin.data.cache.redis;

/** Controls whether an L2 Redis value is written before the caller returns. */
public enum RedisWriteMode {
    /** Write Redis on the calling thread. */
    SYNC,
    /** Put into Caffeine immediately, then write Redis from the background pipeline. */
    ASYNC
}
