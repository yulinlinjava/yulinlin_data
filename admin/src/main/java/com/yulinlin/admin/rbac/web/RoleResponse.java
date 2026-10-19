package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.domain.RoleEntity;

import java.util.List;

public record RoleResponse(String id, String code, String name, List<String> permissions) {
    public static RoleResponse from(RoleEntity role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(),
                role.getPermissions() == null ? List.of() : List.copyOf(role.getPermissions()));
    }
}
