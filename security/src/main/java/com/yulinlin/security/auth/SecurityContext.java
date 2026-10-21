package com.yulinlin.security.auth;

import java.util.Objects;

/** 当前线程身份；不跨线程继承。非 HTTP 调用方必须在 finally 中 clear。 */
public final class SecurityContext {
    private static final ThreadLocal<LoginSession> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RESOLVING = new ThreadLocal<>();

    private SecurityContext() { }

    /** 仅供可信服务端认证代码设置，不能用客户端提交的用户对象构造身份。 */
    public static void set(LoginSession session) {
        clear();
        Objects.requireNonNull(session, "session");
        if (session.expired()) throw new LoginException("登录已失效，请重新登录");
        CURRENT.set(session);
    }

    public static LoginSession getSession() {
        LoginSession session = CURRENT.get();
        SecurityContextResolver resolver = SecurityUtil.contextResolver();
        if (resolver != null) {
            if (Boolean.TRUE.equals(RESOLVING.get())) {
                throw new IllegalStateException("用户加载/身份桥接中不能再次读取 SecurityContext，请使用传入的用户 ID");
            }
            // 校验失败或外部存储异常时，旧身份必须立即失效。
            CURRENT.remove();
            RESOLVING.set(true);
            try {
                session = resolver.resolveContext(session);
            } catch (RuntimeException | Error error) {
                CURRENT.remove();
                throw error;
            } finally {
                RESOLVING.remove();
            }
            if (session != null && !session.expired() && resolver.cacheContext()) CURRENT.set(session);
            else CURRENT.remove();
        }
        if (session != null && session.expired()) {
            clear();
            return null;
        }
        return session;
    }

    public static IUserDetails getUser() {
        LoginSession session = getSession();
        return session == null ? null : session.user();
    }

    public static IUserDetails requireUser() {
        IUserDetails user = getUser();
        if (user == null) throw new LoginException("请先登录或重新登录");
        return user;
    }

    public static String getToken() {
        LoginSession session = getSession();
        return session == null ? null : session.token();
    }

    public static String getUserId() {
        IUserDetails user = getUser();
        return user == null ? null : user.getUserId();
    }

    public static boolean isLogin() { return getUser() != null; }
    /** 只清理本线程快照，不注销外部会话；再次读取可由外部可信身份重新恢复。 */
    public static void clear() { CURRENT.remove(); }
}
