package com.yulinlin.security.limit;

/**
 * Business-owned IP blacklist lookup.
 * Implementations should answer from memory or another low-latency store because this method runs on every request.
 */
@FunctionalInterface
public interface IpBlacklist {

    /** Returns whether the normalized real client IP must be rejected. */
    boolean isBlocked(String ip);
}
