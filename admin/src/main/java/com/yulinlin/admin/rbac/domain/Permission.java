package com.yulinlin.admin.rbac.domain;

import java.util.Arrays;
import java.util.List;

/** 权限字典由代码维护，增加业务接口时在这里增加编码。 */
public enum Permission {
    USER_QUERY("user:query", "查询用户"),
    USER_CREATE("user:create", "创建用户"),
    USER_UPDATE("user:update", "修改用户"),
    USER_DELETE("user:delete", "删除用户"),
    USER_ASSIGN("user:assign", "分配用户角色"),
    USER_PASSWORD("user:password", "重置用户密码"),
    ROLE_QUERY("role:query", "查询角色"),
    ROLE_CREATE("role:create", "创建角色"),
    ROLE_UPDATE("role:update", "修改角色权限"),
    ROLE_DELETE("role:delete", "删除角色");

    private final String code;
    private final String label;

    Permission(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static boolean contains(String code) {
        return Arrays.stream(values()).anyMatch(permission -> permission.code.equals(code));
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Permission::getCode).toList();
    }
}
