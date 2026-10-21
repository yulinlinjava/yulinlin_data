package com.yulinlin.security.auth;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/** 静态便捷入口，只委托 Spring 选定的管理器，不另存用户或 Token。适用于单应用上下文。 */
public final class SecurityUtil implements SmartInitializingSingleton, DisposableBean {
    private static volatile SecurityUtil active;
    private final ObjectProvider<ISecurityManager> managers;
    private ISecurityManager selected;

    public SecurityUtil(ObjectProvider<ISecurityManager> managers) {
        this.managers = managers;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            selected = managers.getObject();
        } catch (NoUniqueBeanDefinitionException error) {
            throw new IllegalStateException("存在多个 ISecurityManager，请只引入一种登录实现，或通过 @Primary 明确选择", error);
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

    /** 上下文桥接跟随唯一选定的管理器；JWT/匿名实现不受影响。 */
    static SecurityContextResolver contextResolver() {
        SecurityUtil binding = active;
        return binding != null && binding.selected instanceof SecurityContextResolver resolver
                ? resolver : null;
    }

    public static LoginResult loginByUsername(String username, String password) {
        return manager().loginByUsername(username, password);
    }
    public static LoginResult loginByUsername(String username, String password, String deviceType) {
        return manager().loginByUsername(username, password, deviceType);
    }
    public static void logout() { manager().logout(); }
    public static IUserDetails getUserDetails() { return manager().getUserDetails(); }
    public static IUserDetails getUserDetails(String userId) { return manager().getUserDetails(userId); }
    public static String getToken() { return manager().getToken(); }
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
