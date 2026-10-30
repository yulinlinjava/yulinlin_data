package com.yulinlin.security.aop;

import com.yulinlin.security.aop.ApiAuthPolicy.Check;
import com.yulinlin.security.aop.ApiAuthPolicy.Policy;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.http.HttpStatus;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
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
            if (!ApiAuthPolicy.hasDeclarations(type)) return false;
            // CGLIB 不会为 final 方法建立拦截链，不能等 matches 被调用才检查它。
            // 提前核对全部公共入口，避免先匹配到其他方法后漏掉无法保护的入口。
            for (Method method : type.getMethods()) {
                if (businessMethod(method)) validateProxyable(method, type, policy(method, type));
            }
            return true;
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
        return ApiAuthPolicy.resolve(method, type);
    }

    private static boolean businessMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                && !ReflectionUtils.isObjectMethod(method);
    }

    private static void validateProxyable(Method method, Class<?> type, Policy policy) {
        if (!policy.login()) return;
        Method actual = ApiAuthPolicy.specificMethod(method, type);
        if (Modifier.isFinal(type.getModifiers()) || Modifier.isFinal(actual.getModifiers())) {
            throw ApiAuthPolicy.invalid(actual, "受保护的类和方法不能是 final，请通过可代理的公共方法访问");
        }
    }

}
