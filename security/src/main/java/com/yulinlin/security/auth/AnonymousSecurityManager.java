package com.yulinlin.security.auth;

/** 默认匿名实现：允许只使用加密、限流的项目启动，但不会放行受保护的方法。 */
public class AnonymousSecurityManager implements ISecurityManager {

    @Override
    public LoginResult login(String userId, String deviceType) {
        SecurityContext.clear();
        throw new IllegalStateException("未配置登录实现，请引入 security-jwt、security-sa-token 或提供 ISecurityManager");
    }

    @Override
    public void logout() {
        SecurityContext.clear();
    }

    @Override
    public IUserDetails getUserDetails() { return null; }

    @Override
    public IUserDetails getUserDetails(String userId) { return null; }

    @Override
    public IUserDetails refresh(String userId) { return null; }
}
