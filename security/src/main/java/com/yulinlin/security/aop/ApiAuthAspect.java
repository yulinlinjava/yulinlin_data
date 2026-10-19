package com.yulinlin.security.aop;

import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.annotation.ApiRole;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 按声明匹配的 Spring AOP 切面，兼容接口、继承和 default 方法。
 * 仅缓存注解策略，不缓存当前用户或鉴权结果；通过代理调用的公共、非 final 方法才受保护。
 */
public final class ApiAuthAspect extends StaticMethodMatcherPointcutAdvisor implements MethodInterceptor {
    private static final long serialVersionUID = 1L;

    /** 在框架同步代理（9998）和业务事务（9999）之前检查权限。 */
    public static final int ORDER = 9990;

    private final Supplier<? extends ISecurityManager> securityManagers;
    private final ThreadLocal<Boolean> resolvingUser = new ThreadLocal<>();
    private final ClassValue<Boolean> candidates = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            if (!hasDeclarations(type)) return false;
            // CGLIB 不会为 final 方法建立拦截链，不能等 matches 被调用才检查它。
            // 提前核对全部公共入口，避免先匹配到其他方法后漏掉无法保护的入口。
            for (Method method : type.getMethods()) {
                if (businessMethod(method)) validateProxyable(method, type, policy(method, type));
            }
            return true;
        }
    };
    private final ClassValue<ConcurrentMap<Method, Policy>> policies = new ClassValue<>() {
        @Override protected ConcurrentMap<Method, Policy> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public ApiAuthAspect(Supplier<? extends ISecurityManager> securityManagers) {
        this.securityManagers = Objects.requireNonNull(securityManagers, "securityManagers");
        setClassFilter(type -> candidates.get(ClassUtils.getUserClass(type)));
        setAdvice(this);
        setOrder(ORDER);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (!businessMethod(method)) return false;
        Policy policy = policy(method, targetClass);
        validateProxyable(method, targetClass, policy);
        return policy.login();
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = Objects.requireNonNull(invocation.getThis(), "鉴权方法必须有目标 Bean");
        Policy policy = policy(invocation.getMethod(), AopUtils.getTargetClass(target));
        if (!policy.login()) return invocation.proceed();

        IUserDetails user = currentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录或重新登录");
        for (Check role : policy.roles()) {
            if (!role.matches(user::hasRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有所需角色");
            }
        }
        for (Check permission : policy.permissions()) {
            if (!permission.matches(user::hasPermission)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有操作权限");
            }
        }
        return invocation.proceed();
    }

    private IUserDetails currentUser() {
        if (Boolean.TRUE.equals(resolvingUser.get())) {
            throw new IllegalStateException("ISecurityManager 获取当前用户时不能再调用受鉴权保护的方法，请直接读取认证数据");
        }
        resolvingUser.set(true);
        try {
            ISecurityManager manager = Objects.requireNonNull(securityManagers.get(), "ISecurityManager 未配置");
            return manager.getUserDetails();
        } finally {
            resolvingUser.remove();
        }
    }

    private Policy policy(Method method, Class<?> type) {
        Class<?> targetClass = ClassUtils.getUserClass(type);
        return policies.get(targetClass).computeIfAbsent(method, key -> resolve(key, targetClass));
    }

    private Policy resolve(Method method, Class<?> targetClass) {
        Method actual = specificMethod(method, targetClass);
        Rules types = new Rules();
        Rules methods = new Rules();
        Set<Method> declarations = new LinkedHashSet<>();
        declarations.add(method);
        declarations.add(actual);
        for (Class<?> type : hierarchy(targetClass)) {
            types.read(type);
            for (Method candidate : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(candidate.getModifiers()) || Modifier.isStatic(candidate.getModifiers())
                        || candidate.isBridge() || candidate.isSynthetic()) continue;
                if (specificMethod(candidate, targetClass).equals(actual)) declarations.add(candidate);
            }
        }
        declarations.forEach(methods::read);
        if (types.publicAccess && types.restricted()) {
            throw invalid(actual, "类上的 ApiPublic 不能与类级登录、角色或权限要求同时声明");
        }
        if (methods.publicAccess && (methods.restricted() || types.hasChecks())) {
            throw invalid(actual, "ApiPublic 只能豁免类级 ApiLogin，不能覆盖方法鉴权或类级角色、权限要求");
        }
        if (methods.publicAccess) return Policy.PUBLIC;

        Set<Check> roles = new LinkedHashSet<>(types.roles);
        roles.addAll(methods.roles);
        Set<Check> permissions = new LinkedHashSet<>(types.permissions);
        permissions.addAll(methods.permissions);
        return new Policy(types.restricted() || methods.restricted(), List.copyOf(roles), List.copyOf(permissions));
    }

    private static Method specificMethod(Method method, Class<?> targetClass) {
        return BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, targetClass));
    }

    private static boolean businessMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                && !ReflectionUtils.isObjectMethod(method);
    }

    private static void validateProxyable(Method method, Class<?> type, Policy policy) {
        if (!policy.login()) return;
        Method actual = specificMethod(method, type);
        if (Modifier.isFinal(type.getModifiers()) || Modifier.isFinal(actual.getModifiers())) {
            throw invalid(actual, "受保护的类和方法不能是 final，请通过可代理的公共方法访问");
        }
    }

    private static boolean hasDeclarations(Class<?> type) {
        for (Class<?> declaration : hierarchy(type)) {
            if (hasAnnotation(declaration)) return true;
            for (Method method : declaration.getDeclaredMethods()) {
                if (businessMethod(method) && hasAnnotation(method)) return true;
            }
        }
        return false;
    }

    private static Set<Class<?>> hierarchy(Class<?> targetClass) {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Class<?> type = targetClass; type != null && type != Object.class; type = type.getSuperclass()) {
            types.add(type);
            types.addAll(ClassUtils.getAllInterfacesForClassAsSet(type));
        }
        return types;
    }

    private static boolean hasAnnotation(AnnotatedElement source) {
        return AnnotatedElementUtils.getMergedAnnotation(source, ApiLogin.class) != null
                || AnnotatedElementUtils.getMergedAnnotation(source, ApiRole.class) != null
                || AnnotatedElementUtils.getMergedAnnotation(source, ApiPerm.class) != null
                || AnnotatedElementUtils.getMergedAnnotation(source, ApiPublic.class) != null;
    }

    private static IllegalStateException invalid(AnnotatedElement source, String reason) {
        return new IllegalStateException("鉴权注解配置错误：" + reason + "，位置：" + source);
    }

    private record Policy(boolean login, List<Check> roles, List<Check> permissions) {
        private static final Policy PUBLIC = new Policy(false, List.of(), List.of());
    }

    private record Check(List<String> values, boolean any) {
        boolean matches(Predicate<String> test) {
            for (String value : values) {
                boolean matched = test.test(value);
                if (any && matched) return true;
                if (!any && !matched) return false;
            }
            return !any;
        }
    }

    private static final class Rules {
        private boolean login;
        private boolean publicAccess;
        private final List<Check> roles = new ArrayList<>();
        private final List<Check> permissions = new ArrayList<>();

        boolean restricted() { return login || hasChecks(); }
        boolean hasChecks() { return !roles.isEmpty() || !permissions.isEmpty(); }

        void read(AnnotatedElement source) {
            login |= AnnotatedElementUtils.getMergedAnnotation(source, ApiLogin.class) != null;
            publicAccess |= AnnotatedElementUtils.getMergedAnnotation(source, ApiPublic.class) != null;
            ApiRole role = AnnotatedElementUtils.getMergedAnnotation(source, ApiRole.class);
            if (role != null) roles.add(check(role.value(), role.any(), source));
            ApiPerm permission = AnnotatedElementUtils.getMergedAnnotation(source, ApiPerm.class);
            if (permission != null) permissions.add(check(permission.value(), permission.any(), source));
        }

        private Check check(String[] values, boolean any, AnnotatedElement source) {
            if (values.length == 0) throw invalid(source, "角色或权限编码不能为空数组");
            Set<String> unique = new LinkedHashSet<>();
            for (String value : values) {
                if (value == null || value.isBlank() || !value.equals(value.strip())) {
                    throw invalid(source, "角色或权限编码不能为空或包含首尾空白");
                }
                unique.add(value);
            }
            return new Check(List.copyOf(unique), any);
        }
    }
}
