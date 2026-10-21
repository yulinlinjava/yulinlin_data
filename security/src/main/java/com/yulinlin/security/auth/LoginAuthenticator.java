package com.yulinlin.security.auth;

/** 业务认证扩展；注册为 Bean 后按精确的登录参数类型分发，不依赖 Java 类名或 Bean 名。 */
public interface LoginAuthenticator<T extends LoginRequest> {
    /** 稳定认证标识，1～64 位字母、数字、点、下划线、冒号或短横线；应用内唯一。 */
    String name();

    /** 负责的具体登录参数类；同一类型只能对应一个认证器。 */
    Class<T> requestType();

    /**
     * 验证凭证、有效期和账号状态，返回不含凭证的安全用户。
     * 拒绝时返回 null 或抛 LoginException；基础设施异常正常传播。
     * 不签发 Token、不递归调用登录；后续权限按 UserStore 或原生会话恢复。
     */
    IUserDetails authenticate(T request);
}
