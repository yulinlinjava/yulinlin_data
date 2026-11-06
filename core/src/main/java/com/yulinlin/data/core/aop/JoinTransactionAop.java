package com.yulinlin.data.core.aop;

import com.yulinlin.data.core.anno.JoinTransaction;
import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import lombok.SneakyThrows;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.*;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/** 两种注解共用一个 ORM 事务入口，不实现 Spring 的事务传播协议。 */
public final class JoinTransactionAop extends StaticMethodMatcherPointcutAdvisor
        implements MethodInterceptor, BeanFactoryAware, SmartInitializingSingleton {
    private final AnnotationTransactionAttributeSource spring = new AnnotationTransactionAttributeSource();
    private final Supplier<RouteSession> routes;
    private ListableBeanFactory beans;

    public JoinTransactionAop() { this(SessionUtil::route); }

    public JoinTransactionAop(Supplier<RouteSession> routes) {
        this.routes = routes;
        setAdvice(this);
        setOrder(9999);
    }

    @Override public boolean matches(Method method, Class<?> targetClass) {
        if (org.springframework.util.ReflectionUtils.isObjectMethod(method)) return false;
        TransactionAttribute attribute = spring.getTransactionAttribute(method, targetClass);
        if (attribute != null) validate(attribute, method);
        return attribute != null || joinAnnotation(method, targetClass) != null;
    }

    private JoinTransaction joinAnnotation(Method method, Class<?> type) {
        Method specific = AopUtils.getMostSpecificMethod(method, type);
        JoinTransaction annotation = AnnotatedElementUtils.findMergedAnnotation(specific, JoinTransaction.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(method, JoinTransaction.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(type, JoinTransaction.class);
        if (annotation != null && annotation.value().length != 0)
            throw new OrmException("@JoinTransaction 不接受数据源列表；一次事务只允许一个 JDBC 数据源：" + method);
        return annotation;
    }

    private void validate(TransactionAttribute attribute, Method method) {
        if (attribute.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED
                || attribute.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT
                || attribute.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT
                || attribute.isReadOnly()
                || (attribute.getQualifier() != null && !attribute.getQualifier().isEmpty())
                || !attribute.getLabels().isEmpty()
                || (attribute instanceof DefaultTransactionAttribute defaults
                    && defaults.getTimeoutString() != null && !defaults.getTimeoutString().isEmpty()))
            throw new OrmException("ORM 仅兼容 @Transactional 的默认配置及回滚规则，不支持 Spring 传播、隔离、超时、只读或事务管理器配置：" + method);
    }

    @Override public Object invoke(MethodInvocation invocation) {
        Class<?> type = AopUtils.getTargetClass(invocation.getThis());
        TransactionAttribute attribute = spring.getTransactionAttribute(invocation.getMethod(), type);
        if (attribute != null) validate(attribute, invocation.getMethod());
        joinAnnotation(invocation.getMethod(), type);
        return routes.get().transaction(() -> proceed(invocation), error -> rollback(attribute, error));
    }

    /** 显式回滚规则优先；未匹配规则时，ORM 默认对所有异常回滚。 */
    private static boolean rollback(TransactionAttribute attribute, Throwable error) {
        RollbackRuleAttribute winner = null;
        int best = Integer.MAX_VALUE;
        if (attribute instanceof RuleBasedTransactionAttribute rules) {
            for (RollbackRuleAttribute rule : rules.getRollbackRules()) {
                int depth = rule.getDepth(error);
                if (depth >= 0 && depth < best) { winner = rule; best = depth; }
            }
        }
        return !(winner instanceof NoRollbackRuleAttribute);
    }

    @SneakyThrows private static Object proceed(MethodInvocation invocation) { return invocation.proceed(); }

    @Override public void setBeanFactory(BeanFactory beanFactory) {
        if (beanFactory instanceof ListableBeanFactory listable) beans = listable;
    }

    @Override public void afterSingletonsInstantiated() {
        if (beans != null && (beans.getBeanNamesForType(TransactionInterceptor.class, true, false).length > 0
                || beans.getBeanNamesForType(BeanFactoryTransactionAttributeSourceAdvisor.class, true, false).length > 0
                || beans.getBeansOfType(Advisor.class, true, false).values().stream()
                    .anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor)))
            throw new OrmException("ORM 注解兼容切面与 Spring 原生事务拦截器不能同时启用；请移除 @EnableTransactionManagement 及原生事务 Advisor");
    }
}
