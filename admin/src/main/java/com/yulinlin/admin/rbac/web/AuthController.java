package com.yulinlin.admin.rbac.web;

import com.yulinlin.admin.rbac.auth.AdminSecurityManager;
import com.yulinlin.admin.rbac.auth.AdminUserDetails;
import com.yulinlin.admin.rbac.domain.UserStatus;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.annotation.ApiLimit;
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.starter.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController("rbacAuthController")
@RequestMapping("/rbac/auth")
@ApiCrypto(false)
@ApiLogin
@RequiredArgsConstructor
public class AuthController {
    private final AdminSecurityManager security;

    @ApiPublic
    @ApiLimit(count = 10, interval = "1m")
    @PostMapping("/login")
    public R<AdminSecurityManager.LoginResponse> login(@Valid @RequestBody RbacRequests.Login request) {
        return R.ok(security.signIn(request.username(), request.password(), request.code()), "登录成功");
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        security.logout();
        return R.ok(null, "注销成功");
    }

    @PostMapping("/me")
    public R<AdminUserDetails> me() { return R.ok(security.getUserDetails()); }

    @PostMapping("/refresh")
    public R<AdminUserDetails> refresh() {
        AdminUserDetails current = security.getUserDetails();
        AdminUserDetails refreshed = current == null ? null : security.refresh(current.getUserId());
        if (refreshed == null || refreshed.getStatus() != UserStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已失效，请重新登录");
        }
        return R.ok(refreshed, "用户信息已刷新");
    }
}
