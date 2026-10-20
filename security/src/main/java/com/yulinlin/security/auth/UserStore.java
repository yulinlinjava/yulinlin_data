package com.yulinlin.security.auth;

/** 业务提供用户资料、角色和权限；不负责签发 Token 或校验登录表单。 */
@FunctionalInterface
public interface UserStore {

    /**
     * 按用户 ID 加载最新安全用户信息，禁止包含密码等凭证。
     * 用户不存在、已禁用或不允许继续登录时返回 null，或抛出 LoginException。
     * 框架每次请求调用一次；业务自行缓存时，应自行处理刷新和失效。
     */
    IUserDetails load(String userId);
}
