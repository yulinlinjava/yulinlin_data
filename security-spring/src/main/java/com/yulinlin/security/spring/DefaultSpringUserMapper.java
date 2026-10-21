package com.yulinlin.security.spring;

import com.yulinlin.security.auth.IUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Set;

/** 仅复制允许对外公开的字段，不暴露 Spring UserDetails 的密码和认证凭证。 */
public final class DefaultSpringUserMapper implements SpringUserMapper {
    @Override
    public IUserDetails map(Authentication authentication) {
        String userId = authentication.getName();
        String username = userId;
        if (authentication.getPrincipal() instanceof IUserDetails user) {
            userId = user.getUserId();
            username = user.getUsername();
        }
        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        if (authentication.getAuthorities() != null) {
            for (GrantedAuthority granted : authentication.getAuthorities()) {
                if (granted == null) continue;
                String authority = granted.getAuthority();
                if (authority == null || authority.isBlank()) continue;
                permissions.add(authority);
                if (authority.startsWith("ROLE_") && authority.length() > 5) {
                    roles.add(authority.substring(5));
                }
            }
        }
        return new UserSnapshot(userId, username, roles, permissions);
    }

    public record UserSnapshot(String userId, String username, Set<String> roles,
                               Set<String> permissions) implements IUserDetails {
        public UserSnapshot {
            roles = Set.copyOf(roles);
            permissions = Set.copyOf(permissions);
        }
        @Override public String getUserId() { return userId; }
        @Override public String getUsername() { return username; }
        @Override public Set<String> getRoles() { return roles; }
        @Override public Set<String> getPermissions() { return permissions; }
        @Override public Object getAttribute(String name) { return null; }
    }
}
