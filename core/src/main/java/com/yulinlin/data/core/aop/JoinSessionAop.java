
package com.yulinlin.data.core.aop;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinSession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** A single routing advisor for concrete classes and proxy-backed Repository interfaces. */
public class JoinSessionAop extends StaticMethodMatcherPointcutAdvisor implements MethodInterceptor {

    private static final int ORDER = 9998;
    private final ConcurrentMap<MethodKey, Optional<Route>> routes = new ConcurrentHashMap<>();

    public JoinSessionAop() {
        setAdvice(this);
        setOrder(ORDER);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return route(method, targetClass).isPresent();
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = invocation.getThis() == null
                ? invocation.getMethod().getDeclaringClass()
                : invocation.getThis().getClass();
        Route selected = route(invocation.getMethod(), targetClass)
                .orElseThrow(() -> new IllegalStateException("Missing @JoinSession route for matched method: "
                        + invocation.getMethod().toGenericString()));
        RouteSession routeSession = SessionUtil.route();
        if (routeSession == null) {
            throw new IllegalStateException("@JoinSession requires a configured RouteSession");
        }
        routeSession.pushAnnotatedSession(selected.group(), selected.cluster());
        try {
            return invocation.proceed();
        } finally {
            routeSession.popAnnotatedSession();
        }
    }

    private Optional<Route> route(Method method, Class<?> targetClass) {
        Class<?> resolvedTarget = targetClass == null ? method.getDeclaringClass() : targetClass;
        MethodKey key = new MethodKey(method, resolvedTarget);
        return routes.computeIfAbsent(key, ignored -> Optional.ofNullable(resolve(method, resolvedTarget)));
    }

    private Route resolve(Method method, Class<?> targetClass) {
        if (ReflectionUtils.isObjectMethod(method)) return null;

        Class<?> userClass = ClassUtils.getUserClass(targetClass);
        Method specific = AopUtils.getMostSpecificMethod(method, userClass);
        JoinSession annotation = find(specific);
        if (annotation == null && !specific.equals(method)) annotation = find(method);

        Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(targetClass);
        Arrays.sort(interfaces, Comparator.comparing(Class::getName));
        if (annotation == null) {
            for (Class<?> interfaceType : interfaces) {
                Method interfaceMethod = ReflectionUtils.findMethod(
                        interfaceType, method.getName(), method.getParameterTypes());
                annotation = find(interfaceMethod);
                if (annotation != null) break;
            }
        }

        if (annotation == null) annotation = find(userClass);
        if (annotation == null && userClass != targetClass) annotation = find(targetClass);
        if (annotation == null) {
            for (Class<?> interfaceType : interfaces) {
                annotation = find(interfaceType);
                if (annotation != null) break;
            }
        }
        return annotation == null ? null : new Route(annotation.value(), annotation.cluster());
    }

    private JoinSession find(java.lang.reflect.AnnotatedElement element) {
        return element == null ? null
                : AnnotatedElementUtils.findMergedAnnotation(element, JoinSession.class);
    }

    private record MethodKey(Method method, Class<?> targetClass) {
    }

    private record Route(String group, JoinCluster cluster) {
    }
}

