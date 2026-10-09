package com.yulinlin.data.core.cache;

import java.util.Locale;

/** Logical data resource used to invalidate related query keys without scanning the cache. */
public record CacheNamespace(String sessionType, String group, String resource)
        implements Comparable<CacheNamespace> {

    public CacheNamespace {
        sessionType = requireText(sessionType, "sessionType");
        group = requireText(group, "group");
        resource = normalizeResource(resource);
    }

    public static CacheNamespace of(Class<?> sessionType, String group, String resource) {
        return new CacheNamespace(sessionType.getName(), group, resource);
    }

    public String value() {
        return sessionType + ':' + group + ':' + resource;
    }

    @Override
    public int compareTo(CacheNamespace other) {
        return value().compareTo(other.value());
    }

    private static String normalizeResource(String value) {
        String resource = requireText(value, "resource");
        boolean quoted = false;
        char quoteEnd = 0;
        int end = resource.length();
        for (int index = 0; index < resource.length(); index++) {
            char current = resource.charAt(index);
            if (!quoted && (current == '`' || current == '"' || current == '[')) {
                quoted = true;
                quoteEnd = current == '[' ? ']' : current;
            } else if (quoted && current == quoteEnd) {
                quoted = false;
            } else if (!quoted && Character.isWhitespace(current)) {
                end = index;
                break;
            }
        }
        return resource.substring(0, end).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
