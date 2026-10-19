package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 请求对象只允许修改明确列出的业务字段，不直接接收 Entity。 */
public final class RbacRequests {
    private RbacRequests() { }

    public record Login(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 32) String code) { }

    public record Id(@NotBlank @Size(max = 128) String id) { }

    public record CreateUser(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotNull @Size(max = 64) String nickname,
            @NotNull @Size(max = 64) List<@NotBlank String> roleCodes) { }

    public record UpdateUser(
            @NotBlank @Size(max = 128) String id,
            @NotNull @Size(max = 64) String nickname) { }

    public record ChangeStatus(
            @NotBlank @Size(max = 128) String id,
            @NotNull UserStatus status) { }

    public record AssignRoles(
            @NotBlank @Size(max = 128) String id,
            @NotNull @Size(max = 64) List<@NotBlank String> roleCodes) { }

    public record ResetPassword(
            @NotBlank @Size(max = 128) String id,
            @NotBlank @Size(min = 12, max = 128) String password) { }

    public record CreateRole(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9:_-]{0,63}") String code,
            @NotBlank @Size(max = 64) String name,
            @NotNull @Size(max = 256) List<@NotBlank String> permissions) { }

    public record UpdateRole(
            @NotBlank @Size(max = 128) String id,
            @NotBlank @Size(max = 64) String name,
            @NotNull @Size(max = 256) List<@NotBlank String> permissions) { }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserPage extends PagePo {
        @Size(max = 64)
        @JoinWhere(condition = ConditionEnum.like)
        private String username;

        @JoinWhere
        private UserStatus status;
    }
}
