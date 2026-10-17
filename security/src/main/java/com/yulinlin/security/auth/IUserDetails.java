package com.yulinlin.security.auth;

import java.util.Set;

/**
 * Minimal authenticated-user model implemented by the application.
 * It deliberately excludes credentials, tokens and request-specific device or network data.
 */
public interface IUserDetails {

    /** Stable application user identifier. */
    String getUserId();

    /** Login or display name. */
    String getUsername();

    /** Unique role identifiers owned by the application. */
    Set<String> getRoles();

    /** Unique permission identifiers owned by the application. */
    Set<String> getPermissions();

    /** Returns whether this user owns the exact, case-sensitive role identifier. */
    default boolean hasRole(String role) {
        Set<String> roles = getRoles();
        return role != null && roles != null && roles.contains(role);
    }

    /** Returns whether this user owns the exact, case-sensitive permission identifier. */
    default boolean hasPermission(String permission) {
        Set<String> permissions = getPermissions();
        return permission != null && permissions != null && permissions.contains(permission);
    }

    /**
     * Reads one application-specific attribute without exposing its backing storage.
     * Unknown attributes should return {@code null}.
     */
    Object getAttribute(String name);
}
