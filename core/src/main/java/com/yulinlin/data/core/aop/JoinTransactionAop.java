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
import java.time.Duration;
import java.util.function.Supplier;

/** 两种注解共用一个 ORM 事务入口，不实现 Spring 的事务传播协议。 */
public final class JoinTransactionAop extends StaticMethodMatcherPointcutAdvisor
        implements MethodInterceptor, BeanFactoryAware, SmartInitializingSingleton {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JoinTransactionAop.class);
    private final AnnotationTransactionAttributeSource spring = new AnnotationTransactionAttributeSource();
    /** 已经提示过“配置被忽略”的方法，每个方法只打一次日志。 */
    private final java.util.Set<Method> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
        TransactionAttribute attribute = attribute(method, targetClass);
        if (attribute != null) validate(attribute, method);
        JoinTransaction join = joinAnnotation(method, targetClass);
        if (attribute == null && join == null) return false;
        timeout(attribute, join, method); // 启动时校验超时配置
        return true;
    }

    /** Spring 解析 timeoutString 失败时抛 IllegalArgumentException，统一转成 OrmException。 */
    private TransactionAttribute attribute(Method method, Class<?> type) {
        try { return spring.getTransactionAttribute(method, type); }
        catch (IllegalArgumentException e) { throw new OrmException("@Transactional 配置无效（timeoutString 必须是整数秒）：" + method, e); }
    }

    private JoinTransaction joinAnnotation(Method method, Class<?> type) {
        Method specific = AopUtils.getMostSpecificMethod(method, type);
        JoinTransaction annotation = AnnotatedElementUtils.findMergedAnnotation(specific, JoinTransaction.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(method, JoinTransaction.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(type, JoinTransaction.class);
        if (annotation != null && annotation.value().length != 0)
            warnOnce(method, "@JoinTransaction 的数据源列表 " + java.util.Arrays.toString(annotation.value()) + " 被忽略，事务按实际访问自动登记数据源");
        return annotation;
    }

    /**
     * ORM 只使用 @Transactional 的回滚规则和超时。传播方式、隔离级别、事务管理器、label 不生效，
     * 打一次 WARN 日志后忽略；readOnly 静默忽略。
     */
    private void validate(TransactionAttribute attribute, Method method) {
        var ignored = new java.util.ArrayList<String>();
        if (attribute.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED) ignored.add("propagation");
        if (attribute.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) ignored.add("isolation");
        if (attribute.getQualifier() != null && !attribute.getQualifier().isEmpty()) ignored.add("transactionManager");
        if (!attribute.getLabels().isEmpty()) ignored.add("label");
        if (!ignored.isEmpty())
            warnOnce(method, "@Transactional 的 " + String.join("、", ignored) + " 不生效，按 ORM 默认事务执行（自动嵌套保存点）");
    }

    private void warnOnce(Method method, String message) {
        if (warned.add(method)) log.warn("{}：{}", message, method);
    }

    /**
     * 事务超时：@Transactional 的 timeout / timeoutString 优先，其次 @JoinTransaction.timeout；都没有时返回 null。
     */
    private static Duration timeout(TransactionAttribute attribute, JoinTransaction join, Method method) {
        int seconds = TransactionDefinition.TIMEOUT_DEFAULT;
        if (attribute != null) {
            seconds = attribute.getTimeout();
            if (seconds == TransactionDefinition.TIMEOUT_DEFAULT && attribute instanceof DefaultTransactionAttribute defaults
                    && defaults.getTimeoutString() != null && !defaults.getTimeoutString().isBlank()) {
                try { seconds = Integer.parseInt(defaults.getTimeoutString().strip()); }
                catch (NumberFormatException e) { throw new OrmException("@Transactional 的 timeoutString 必须是整数秒：" + method); }
            }
        }
        if (seconds == TransactionDefinition.TIMEOUT_DEFAULT && join != null) seconds = join.timeout();
        if (seconds == TransactionDefinition.TIMEOUT_DEFAULT) return null;
        if (seconds <= 0) throw new OrmException("事务超时必须大于 0 秒（-1 表示不设置）：" + method);
        return Duration.ofSeconds(seconds);
    }

    @Override public Object invoke(MethodInvocation invocation) {
        Class<?> type = AopUtils.getTargetClass(invocation.getThis());
        TransactionAttribute attribute = attribute(invocation.getMethod(), type);
        if (attribute != null) validate(attribute, invocation.getMethod());
        JoinTransaction join = joinAnnotation(invocation.getMethod(), type);
        Duration timeout = timeout(attribute, join, invocation.getMethod());
        return routes.get().transaction(() -> proceed(invocation), error -> rollback(attribute, error), timeout);
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
        // 让 timeoutString 中的 ${...} 占位符按应用配置解析
        if (beanFactory instanceof org.springframework.beans.factory.config.ConfigurableBeanFactory configurable)
            spring.setEmbeddedValueResolver(new org.springframework.beans.factory.config.EmbeddedValueResolver(configurable));
    }

    @Override public void afterSingletonsInstantiated() {
        if (beans != null && (beans.getBeanNamesForType(TransactionInterceptor.class, true, false).length > 0
                || beans.getBeanNamesForType(BeanFactoryTransactionAttributeSourceAdvisor.class, true, false).length > 0
                || beans.getBeansOfType(Advisor.class, true, false).values().stream()
                    .anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor)))
            throw new OrmException("ORM 注解兼容切面与 Spring 原生事务拦截器不能同时启用；请移除 @EnableTransactionManagement 及原生事务 Advisor");
    }
}
