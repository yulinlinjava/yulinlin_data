package com.yulinlin.monitor;

/** Checks a final MySQL SQL template (with ? placeholders), never its bound values.
 * Custom Spring beans replace the built-in detector. Implementations must be thread-safe. */
@FunctionalInterface
public interface SqlRiskDetector {
    SqlRiskResult check(String sql);
}
