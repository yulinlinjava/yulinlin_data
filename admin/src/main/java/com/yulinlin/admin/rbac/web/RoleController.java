package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.domain.Permission;
import com.yulinlin.admin.rbac.service.RoleService;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.starter.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController("rbacRoleController")
@RequestMapping("/rbac/roles")
@ApiCrypto(false)
@ApiLogin
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roles;

    @ApiPerm("role:query")
    @PostMapping("/list")
    public R<List<RoleResponse>> list() { return R.ok(roles.list()); }

    @ApiPerm("role:query")
    @PostMapping("/page")
    public R<Page<RoleResponse>> page(@Valid @RequestBody RbacRequests.RolePage request) {
        return R.ok(roles.page(request));
    }

    @ApiPerm("role:query")
    @PostMapping("/detail")
    public R<RoleResponse> detail(@Valid @RequestBody RbacRequests.Id request) {
        return R.ok(roles.detail(request.id()));
    }

    @ApiPerm("role:query")
    @PostMapping("/permissions")
    public R<List<PermissionOption>> permissions() {
        return R.ok(Arrays.stream(Permission.values())
                .map(permission -> new PermissionOption(permission.getCode(), permission.getLabel())).toList());
    }

    @ApiPerm("role:create")
    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody RbacRequests.CreateRole request) {
        return R.ok(roles.create(request), "角色创建成功");
    }

    @ApiPerm("role:create")
    @PostMapping("/createBatch")
    public R<List<String>> createBatch(@Valid @RequestBody RbacRequests.Batch<RbacRequests.CreateRole> request) {
        return R.ok(roles.createBatch(request.items()), "批量创建角色成功");
    }

    @ApiPerm("role:update")
    @PostMapping("/update")
    public R<Void> update(@Valid @RequestBody RbacRequests.UpdateRole request) {
        roles.update(request);
        return R.ok();
    }

    @ApiPerm("role:update")
    @PostMapping("/updateBatch")
    public R<Integer> updateBatch(@Valid @RequestBody RbacRequests.Batch<RbacRequests.UpdateRole> request) {
        return R.ok(roles.updateBatch(request.items()), "批量修改角色成功");
    }

    @ApiPerm("role:delete")
    @PostMapping("/delete")
    public R<Void> delete(@Valid @RequestBody RbacRequests.Id request) {
        roles.delete(request.id());
        return R.ok();
    }

    @ApiPerm("role:delete")
    @PostMapping("/deleteBatch")
    public R<Integer> deleteBatch(@Valid @RequestBody RbacRequests.Ids request) {
        return R.ok(roles.deleteBatch(request.ids()), "批量删除角色成功");
    }

    public record PermissionOption(String code, String label) { }
}
