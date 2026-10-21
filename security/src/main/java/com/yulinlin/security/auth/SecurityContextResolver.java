package com.yulinlin.security.auth;

/** 可选的外部身份桥接；仅 Spring 选定的 ISecurityManager 实现此接口时生效。 */
@FunctionalInterface
public interface SecurityContextResolver {
    /**
     * 以外部框架当前身份为准校验并刷新快照；身份未变时可复用 cached.user()。
     * 未登录/已注销返回 null；存储故障等异常应正常抛出，不能退回旧身份。
     * 不能调用 SecurityContext 的读取方法，否则会递归解析。
     */
    LoginSession resolveContext(LoginSession cached);

    /** 是否保留请求内快照；原生上下文本身已保存完整身份的适配器可关闭，避免再持有一份线程数据。 */
    default boolean cacheContext() { return true; }
}
