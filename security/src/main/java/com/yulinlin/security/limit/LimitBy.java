package com.yulinlin.security.limit;

/** Supported client dimensions for API rate limiting. */
public enum LimitBy {
    /** Trusted proxy-aware client IP. */
    IP,
    /** Device-Id header; missing or invalid values fall back to IP. */
    DEVICE
}
