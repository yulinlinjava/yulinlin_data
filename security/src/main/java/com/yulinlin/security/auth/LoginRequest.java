package com.yulinlin.security.auth;

/** 登录参数协议；按服务端注册的 LoginAuthenticator 分发，不接受客户端指定任意 Java 类型。 */
public interface LoginRequest {
    /** 登录元数据，不是认证凭证；省略时内置参数类使用 default。 */
    String deviceType();
}
