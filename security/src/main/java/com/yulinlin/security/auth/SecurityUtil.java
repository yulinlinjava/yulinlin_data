package com.yulinlin.security.auth;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import com.yulinlin.security.session.LoginSessionManager;
import com.yulinlin.security.session.SessionManagementProperties;

/** 静态便捷入口，只委托 Spring 选定的管理器，不另存用户或 Token。适用于单应用上下文。 */
public final class SecurityUtil implements SmartInitializingSingleton, DisposableBean {
    private static volatile SecurityUtil active;
    private final ObjectProvider<ISecurityManager> managers;
    private ISecurityManager selected;
    private final ObjectProvider<LoginSessionManager> sessionManagers;
    private final SessionManagementProperties sessionProperties;
    private LoginSessionManager sessions;

    public SecurityUtil(ObjectProvider<ISecurityManager> managers) {
        this(managers, null, new SessionManagementProperties());
    }

    public SecurityUtil(ObjectProvider<ISecurityManager> managers, ObjectProvider<LoginSessionManager> sessionManagers,
                        SessionManagementProperties sessionProperties) {
        this.managers = managers;
        this.sessionManagers = sessionManagers;
        this.sessionProperties = sessionProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        var candidates = managers.stream().toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException("必须且只能有一个 ISecurityManager，不能通过 @Primary 混用多个安全实现");
        }
        selected = candidates.getFirst();
        sessionProperties.validate();
        var sessionCandidates = sessionManagers == null ? java.util.List.<LoginSessionManager>of()
                : sessionManagers.stream().toList();
        if (sessionCandidates.size() > 1) {
            throw new IllegalStateException("只能有一个 LoginSessionManager，不能通过 @Primary 选择多个会话管理器");
        }
        if (sessionProperties.isEnabled(selected.sessionManagementEnabledByDefault())) {
            if (sessionCandidates.isEmpty()) {
                throw new IllegalStateException("已开启统一会话管理，但当前安全实现没有 LoginSessionManager；JWT/匿名实现不支持此功能");
            }
            sessions = sessionCandidates.getFirst();
        }
        synchronized (SecurityUtil.class) {
            if (active != null && active != this) {
                throw new IllegalStateException("SecurityUtil 已绑定其他应用上下文，多上下文应用请直接注入 ISecurityManager");
            }
            active = this;
        }
    }

    public static ISecurityManager manager() {
        SecurityUtil binding = active;
        if (binding == null) throw new IllegalStateException("安全管理器尚未初始化或应用已关闭");
        return binding.selected;
    }

    /** 关闭或不支持时明确失败，不返回一个静默空实现。 */
    public static LoginSessionManager sessions() {
        SecurityUtil binding = active;
        if (binding == null) throw new IllegalStateException("安全管理器尚未初始化或应用已关闭");
        if (binding.sessions == null) throw new UnsupportedOperationException("统一会话管理未开启或当前实现不支持");
        return binding.sessions;
    }

    /** 上下文桥接跟随唯一选定的管理器；JWT/匿名实现不受影响。 */
    static SecurityContextResolver contextResolver() {
        SecurityUtil binding = active;
        return binding != null && binding.selected instanceof SecurityContextResolver resolver
                ? resolver : null;
    }

    public static LoginResult login(LoginRequest request) { return manager().login(request); }

    public static void logout() { manager().logout(); }
    public static IUserDetails getUserDetails() { return manager().getUserDetails(); }
    public static IUserDetails getUserDetails(String userId) { return manager().getUserDetails(userId); }
    public static String getToken() { return manager().getToken(); }
    public static String getAuthType() { return manager().getAuthType(); }
    public static IUserDetails requireUser() { return manager().requireUser(); }
    public static boolean isLogin() { return manager().isLogin(); }
    public static IUserDetails refresh() { return manager().refresh(); }
    public static IUserDetails refresh(String userId) { return manager().refresh(userId); }
    public static String getUserId() {
        IUserDetails user = getUserDetails();
        return user == null ? null : user.getUserId();
    }
    public static boolean hasRole(String role) {
        IUserDetails user = getUserDetails();
        return user != null && user.hasRole(role);
    }
    public static boolean hasPermission(String permission) {
        IUserDetails user = getUserDetails();
        return user != null && user.hasPermission(permission);
    }

    @Override
    public void destroy() {
        synchronized (SecurityUtil.class) {
            if (active == this) active = null;
        }
    }
}
