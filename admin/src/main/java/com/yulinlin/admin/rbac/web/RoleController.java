package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.auth.RequirePermission;
import com.yulinlin.admin.rbac.domain.Permission;
import com.yulinlin.admin.rbac.service.RoleService;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.starter.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController("rbacRoleController")
@RequestMapping("/rbac/roles")
@Profile("rbac")
@ApiCrypto(false)
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roles;

    @RequirePermission(Permission.ROLE_QUERY)
    @PostMapping("/list")
    public R<List<RoleResponse>> list() { return R.ok(roles.list()); }

    @RequirePermission(Permission.ROLE_QUERY)
    @PostMapping("/permissions")
    public R<List<PermissionOption>> permissions() {
        return R.ok(Arrays.stream(Permission.values())
                .map(permission -> new PermissionOption(permission.getCode(), permission.getLabel())).toList());
    }

    @RequirePermission(Permission.ROLE_CREATE)
    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody RbacRequests.CreateRole request) {
        return R.ok(roles.create(request), "角色创建成功");
    }

    @RequirePermission(Permission.ROLE_UPDATE)
    @PostMapping("/update")
    public R<Void> update(@Valid @RequestBody RbacRequests.UpdateRole request) {
        roles.update(request);
        return R.ok();
    }

    @RequirePermission(Permission.ROLE_DELETE)
    @PostMapping("/delete")
    public R<Void> delete(@Valid @RequestBody RbacRequests.Id request) {
        roles.delete(request.id());
        return R.ok();
    }

    public record PermissionOption(String code, String label) { }
}
