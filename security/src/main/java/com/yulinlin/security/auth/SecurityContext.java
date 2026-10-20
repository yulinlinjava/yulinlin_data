package com.yulinlin.security.auth;

import java.util.Objects;

/** 当前线程身份；不跨线程继承。非 HTTP 调用方必须在 finally 中 clear。 */
public final class SecurityContext {
    private static final ThreadLocal<LoginSession> CURRENT = new ThreadLocal<>();

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
    public static void clear() { CURRENT.remove(); }
}
