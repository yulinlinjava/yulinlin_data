package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.admin.rbac.service.UserService;
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

@RestController("rbacUserController")
@RequestMapping("/rbac/users")
@ApiCrypto(false)
@ApiLogin
@RequiredArgsConstructor
public class UserController {
    private final UserService users;

    @ApiPerm("user:query")
    @PostMapping("/list")
    public R<List<UserResponse>> list(@Valid @RequestBody RbacRequests.UserPage request) {
        return R.ok(users.list(request));
    }

    @ApiPerm("user:query")
    @PostMapping("/page")
    public R<Page<UserResponse>> page(@Valid @RequestBody RbacRequests.UserPage request) {
        return R.ok(users.page(request));
    }

    @ApiPerm("user:query")
    @PostMapping("/detail")
    public R<UserResponse> detail(@Valid @RequestBody RbacRequests.Id request) {
        return R.ok(users.detail(request.id()));
    }

    @ApiPerm("user:query")
    @PostMapping("/statuses")
    public R<List<StatusOption>> statuses() {
        return R.ok(Arrays.stream(UserStatus.values())
                .map(status -> new StatusOption(status.getValue(), status.getLabel())).toList());
    }

    @ApiPerm("user:create")
    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody RbacRequests.CreateUser request) {
        return R.ok(users.create(request), "用户创建成功");
    }

    @ApiPerm("user:create")
    @PostMapping("/createBatch")
    public R<List<String>> createBatch(@Valid @RequestBody RbacRequests.Batch<RbacRequests.CreateUser> request) {
        return R.ok(users.createBatch(request.items()), "批量创建用户成功");
    }

    @ApiPerm("user:update")
    @PostMapping("/update")
    public R<Void> update(@Valid @RequestBody RbacRequests.UpdateUser request) {
        users.update(request);
        return R.ok();
    }

    @ApiPerm("user:update")
    @PostMapping("/updateBatch")
    public R<Integer> updateBatch(@Valid @RequestBody RbacRequests.Batch<RbacRequests.UpdateUser> request) {
        return R.ok(users.updateBatch(request.items()), "批量修改用户成功");
    }

    @ApiPerm("user:update")
    @PostMapping("/status")
    public R<Void> status(@Valid @RequestBody RbacRequests.ChangeStatus request) {
        users.status(request);
        return R.ok();
    }

    @ApiPerm("user:assign")
    @PostMapping("/roles")
    public R<Void> roles(@Valid @RequestBody RbacRequests.AssignRoles request) {
        users.assignRoles(request);
        return R.ok();
    }

    @ApiPerm("user:password")
    @PostMapping("/password")
    public R<Void> password(@Valid @RequestBody RbacRequests.ResetPassword request) {
        users.resetPassword(request);
        return R.ok();
    }

    @ApiPerm("user:delete")
    @PostMapping("/delete")
    public R<Void> delete(@Valid @RequestBody RbacRequests.Id request) {
        users.delete(request.id());
        return R.ok();
    }

    @ApiPerm("user:delete")
    @PostMapping("/deleteBatch")
    public R<Integer> deleteBatch(@Valid @RequestBody RbacRequests.Ids request) {
        return R.ok(users.deleteBatch(request.ids()), "批量删除用户成功");
    }

    public record StatusOption(String value, String label) { }
}
