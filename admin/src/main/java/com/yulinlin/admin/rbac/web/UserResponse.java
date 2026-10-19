package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.domain.UserEntity;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.data.lang.util.DateTime;

import java.util.List;

/** 在 Service 内转换，密码哈希和关联代理不进入 Controller 响应。 */
public record UserResponse(String id, String username, String nickname, UserStatus status,
                           String statusLabel, List<String> roleCodes, DateTime crtTime, DateTime uptTime) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getStatus(),
                user.getStatus() == null ? "未知" : user.getStatus().getLabel(),
                user.getRoleCodes() == null ? List.of() : List.copyOf(user.getRoleCodes()),
                user.getCrtTime(), user.getUptTime());
    }
}
