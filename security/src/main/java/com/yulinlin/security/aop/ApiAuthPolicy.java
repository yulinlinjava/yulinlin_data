package com.yulinlin.security.aop;

import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.annotation.ApiRole;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/** 框架内部共用注解策略：MVC 可选身份恢复与 AOP 鉴权必须采用同一继承、覆盖和冲突规则。 */
public final class ApiAuthPolicy {
    private static final ClassValue<ConcurrentMap<Method, Policy>> POLICIES = new ClassValue<>() {
        @Override protected ConcurrentMap<Method, Policy> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private ApiAuthPolicy() { }

    /** 只判断此入口是否允许匿名；不代表其后调用的 Service 方法也允许匿名。 */
    public static boolean allowsAnonymous(Method method, Class<?> type) {
        return !resolve(method, type).login();
    }

    static Policy resolve(Method method, Class<?> type) {
        Class<?> targetClass = ClassUtils.getUserClass(type);
        return POLICIES.get(targetClass).computeIfAbsent(method, key -> resolvePolicy(key, targetClass));
    }

    private static Policy resolvePolicy(Method method, Class<?> targetClass) {
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

    static Method specificMethod(Method method, Class<?> targetClass) {
        return BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, targetClass));
    }

    static boolean hasDeclarations(Class<?> type) {
        for (Class<?> declaration : hierarchy(type)) {
            if (hasAnnotation(declaration)) return true;
            for (Method method : declaration.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                        && hasAnnotation(method)) return true;
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

    static IllegalStateException invalid(AnnotatedElement source, String reason) {
        return new IllegalStateException("鉴权注解配置错误：" + reason + "，位置：" + source);
    }

    record Policy(boolean login, List<Check> roles, List<Check> permissions) {
        private static final Policy PUBLIC = new Policy(false, List.of(), List.of());
    }

    record Check(List<String> values, boolean any) {
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
