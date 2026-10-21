package com.yulinlin.security.auth;

/** 账号密码登录；不修改原始密码，不将凭证放入用户资料或日志。 */
public record PasswordLogin(String username, String password, String deviceType) implements LoginRequest {
    public PasswordLogin(String username, String password) {
        this(username, password, "default");
    }

    public PasswordLogin {
        if (deviceType == null) deviceType = "default";
    }

    @Override public String toString() { return "PasswordLogin[凭证已隐藏]"; }
}
