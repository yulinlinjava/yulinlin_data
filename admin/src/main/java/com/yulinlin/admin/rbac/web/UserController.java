package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.auth.RequirePermission;
import com.yulinlin.admin.rbac.domain.Permission;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.service.UserService;
import com.yulinlin.data.lang.util.Page;
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

@RestController("rbacUserController")
@RequestMapping("/rbac/users")
@Profile("rbac")
@ApiCrypto(false)
@RequiredArgsConstructor
public class UserController {
    private final UserService users;

    @RequirePermission(Permission.USER_QUERY)
    @PostMapping("/page")
    public R<Page<UserResponse>> page(@Valid @RequestBody RbacRequests.UserPage request) {
        return R.ok(users.page(request));
    }

    @RequirePermission(Permission.USER_QUERY)
    @PostMapping("/detail")
    public R<UserResponse> detail(@Valid @RequestBody RbacRequests.Id request) {
        return R.ok(users.detail(request.id()));
    }

    @RequirePermission(Permission.USER_QUERY)
    @PostMapping("/statuses")
    public R<List<StatusOption>> statuses() {
        return R.ok(Arrays.stream(UserStatus.values())
                .map(status -> new StatusOption(status.getValue(), status.getLabel())).toList());
    }

    @RequirePermission(Permission.USER_CREATE)
    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody RbacRequests.CreateUser request) {
        return R.ok(users.create(request), "用户创建成功");
    }

    @RequirePermission(Permission.USER_UPDATE)
    @PostMapping("/update")
    public R<Void> update(@Valid @RequestBody RbacRequests.UpdateUser request) {
        users.update(request);
        return R.ok();
    }

    @RequirePermission(Permission.USER_UPDATE)
    @PostMapping("/status")
    public R<Void> status(@Valid @RequestBody RbacRequests.ChangeStatus request) {
        users.status(request);
        return R.ok();
    }

    @RequirePermission(Permission.USER_ASSIGN)
    @PostMapping("/roles")
    public R<Void> roles(@Valid @RequestBody RbacRequests.AssignRoles request) {
        users.assignRoles(request);
        return R.ok();
    }

    @RequirePermission(Permission.USER_PASSWORD)
    @PostMapping("/password")
    public R<Void> password(@Valid @RequestBody RbacRequests.ResetPassword request) {
        users.resetPassword(request);
        return R.ok();
    }

    @RequirePermission(Permission.USER_DELETE)
    @PostMapping("/delete")
    public R<Void> delete(@Valid @RequestBody RbacRequests.Id request) {
        users.delete(request.id());
        return R.ok();
    }

    public record StatusOption(String value, String label) { }
}
