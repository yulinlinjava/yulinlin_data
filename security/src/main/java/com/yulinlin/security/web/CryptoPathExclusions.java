package com.yulinlin.security.web;

import com.yulinlin.security.SecurityCryptoProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;

/** Matches server-configured paths that bypass request and response cryptography. */
public final class CryptoPathExclusions {

    private final List<PathPattern> patterns;

    public CryptoPathExclusions(SecurityCryptoProperties properties) {
        this(properties.getExcludePaths());
    }

    public CryptoPathExclusions(List<String> excludePaths) {
        PathPatternParser parser = new PathPatternParser();
        List<PathPattern> compiled = new ArrayList<>();
        if (excludePaths != null) {
            for (String candidate : excludePaths) {
                if (candidate == null || candidate.isBlank()) continue;
                String pattern = normalize(candidate.trim());
                compiled.add(parser.parse(pattern));
            }
        }
        this.patterns = List.copyOf(compiled);
    }

    public boolean isCurrentRequestExcluded() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return matches(path);
    }

    public boolean isExcluded(HttpInputMessage inputMessage) {
        if (isCurrentRequestExcluded()) return true;
        return inputMessage instanceof ServerHttpRequest request && matches(request.getURI().getPath());
    }

    public boolean isExcluded(ServerHttpRequest request) {
        if (isCurrentRequestExcluded()) return true;
        return request != null && matches(request.getURI().getPath());
    }

    public boolean matches(String requestPath) {
        if (requestPath == null || requestPath.isBlank() || patterns.isEmpty()) return false;
        PathContainer path = PathContainer.parsePath(normalize(requestPath));
        for (PathPattern pattern : patterns) {
            if (pattern.matches(path)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }
}
