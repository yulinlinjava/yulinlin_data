package com.yulinlin.security.auth;

/** 按稳定用户 ID 提供资料、角色和权限；可与 UserAuthenticator 由同一业务 Bean 实现。 */
@FunctionalInterface
public interface UserStore {

    /**
     * 按用户 ID 加载最新安全用户信息，禁止包含密码等凭证。
     * userId 来自已验证的 Token/会话，不是 username；改用户名后仍通过同一 ID 恢复用户。
     * 本方法不校验登录密码、不签发 Token；账号密码登录由 UserAuthenticator 负责。
     * 用户不存在、已禁用或不允许继续登录时返回 null，或抛出 LoginException。
     * 同一请求、同一身份通常只加载一次；切换账号或主动 refresh 会重新加载。
     * 实现应使用传入的 userId，不能再次读取 SecurityContext/SecurityUtil 的当前用户。
     * 业务自行缓存时，应自行处理刷新和失效。
     */
    IUserDetails load(String userId);
}
