package com.yulinlin.security.auth;

/** JWT / Sa-Token 的业务账号认证扩展；可与 UserStore 由同一业务 Bean 实现，两个方法职责独立。 */
@FunctionalInterface
public interface UserAuthenticator {
    /**
     * 根据 username 查找账号，校验密码与账号状态，返回含真实 userId、角色权限且不含密码的安全用户。
     * 不能假定 username 等于 userId；返回的 ID 用于建立登录及后续 UserStore.load(userId)。
     * 认证拒绝时返回 null 或抛 LoginException，基础设施故障正常抛出。
     * 验证码由业务在调用 loginByUsername 之前校验；不得只按用户名查到用户就返回，
     * 也不能反向调用 SecurityUtil.loginByUsername 造成递归。
     */
    IUserDetails authenticate(String username, String password);
}
