package com.yulinlin.security.limit;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the client address for rate limiting and IP blacklists. Implementations must be thread-safe. */
@FunctionalInterface
public interface IpResolver {
    /** Returns the client address, or null when no request is available. */
    String resolve(HttpServletRequest request);
}
