package com.yulinlin.data.core.proxy;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListener;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import com.yulinlin.data.core.wrapper.factory.UpdatePatchFactory;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Setter-only change tracking, scoped to one outer route transaction and data source. */
class SyncProxyFactory implements IProxyFactory, TransactionListener {
    private final ThreadLocal<Changes> local = new ThreadLocal<>();
    private static final ClassValue<Plan> PLANS = new ClassValue<>() {
        @Override protected Plan computeValue(Class<?> type) { return new Plan(type); }
    };
    private static final class Plan {
        final Class<?> type;
        final Map<String, Field> setters = new HashMap<>();
        final List<Field> keys = new ArrayList<>(), versions = new ArrayList<>();
        Plan(Class<?> type) {
            this.type = type;
            if (AnnotationUtil.findAnnotation(type, JoinTable.class) == null)
                throw new NoticeException("懒同步实体缺少JoinTable注解: " + type.getName());
            for (Field field : ReflectionUtil.getAllDeclaredFields(type)) {
                if (!UpdatePatchFactory.persistent(field)) continue;
                JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
                JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
                if (meta != null && meta.primaryKey()) keys.add(field);
                if (mapping != null && mapping.version() && mapping.update()) {
                    if (meta != null && meta.primaryKey()) throw new NoticeException("主键不能同时作为版本字段: " + field);
                    if (field.getType() != Integer.class && field.getType() != int.class
                            && field.getType() != Long.class && field.getType() != long.class)
                        throw new NoticeException("懒同步版本字段仅支持 Integer/int 或 Long/long: " + field);
                    versions.add(field);
                }
                if (mapping == null || mapping.update() || (meta != null && meta.primaryKey())) {
                    String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
                    setters.put("set" + suffix, field);
                }
            }
            if (keys.isEmpty()) throw new NoticeException("懒同步实体缺少JoinMeta(primaryKey = true): " + type.getName());
        }
    }
    private final class Changes {
        final RouteSession route;
        final Object transaction;
        final Thread owner = Thread.currentThread();
        final Map<String, IdentityHashMap<Object, SyncProxy>> sources = new LinkedHashMap<>();
        final List<SyncProxy> tracked = new ArrayList<>();
        boolean flushing, finished, springRegistered, springCompleted;
        Changes(RouteSession route) { this.route = route; this.transaction = route.transactionIdentity(); }
        void check() {
            if (finished || springCompleted || Thread.currentThread() != owner || SessionUtil.route() != route
                    || route.transactionIdentity() != transaction)
                throw new NoticeException("懒同步 setter 必须在创建代理的线程及原始事务内执行");
        }
        void joinSpring() {
            if (springRegistered || !TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isSynchronizationActive()) return;
            springRegistered = true;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    if (finished || route.transactionIdentity() != transaction) return;
                    if (readOnly && tracked.stream().anyMatch(row -> !row.dirty.isEmpty()))
                        throw new NoticeException("只读事务不能执行懒同步更新");
                    if (!route.isRollbackOnly()) SyncProxyFactory.this.flush(Changes.this);
                }
                @Override public void afterCompletion(int status) {
                    springCompleted = true;
                    if (status != STATUS_COMMITTED && route.transactionIdentity() == transaction) route.setRollbackOnly();
                }
            });
        }
    }

    private Changes changes() {
        RouteSession route = SessionUtil.route();
        if (route == null || !route.isOpenTransaction()) throw new NoticeException("需要开启事务");
        Changes changes = local.get();
        if (changes == null || changes.finished || changes.transaction != route.transactionIdentity() || changes.route != route) {
            changes = new Changes(route);
            local.set(changes);
        }
        changes.check();
        changes.joinSpring();
        return changes;
    }

    @SuppressWarnings("unchecked")
    public <E> E getProxy(String session, E data) {
        if (data == null) return null;
        Changes changes = changes();
        IdentityHashMap<Object, SyncProxy> cache = changes.sources.computeIfAbsent(session, ignored -> new IdentityHashMap<>());
        SyncProxy proxy = cache.get(data);
        if (proxy == null) {
            proxy = new SyncProxy(changes, session, data);
            changes.tracked.add(proxy);
            cache.put(data, proxy);
            cache.put(proxy.proxy, proxy);
        }
        return (E) proxy.proxy;
    }
    @Override public <E> E getProxy(E data) { return getProxy(SessionUtil.nowSession(), data); }
    public <E> List<E> getProxyList(String session, List<E> data) {
        List<E> result = new ArrayList<>(data.size());
        for (E value : data) result.add(getProxy(session, value));
        return result;
    }
    @Override public <E> List<E> getProxyList(List<E> data) { return getProxyList(SessionUtil.nowSession(), data); }
    @Override public void startTransaction() { changes(); }
    @Override public void commitTransaction() {
        Changes changes = local.get();
        if (changes != null && !changes.finished && changes.route.isOutermostTransaction()) flush(changes);
    }
    @Override public void rollbackTransaction() {
        Changes changes = local.get();
        if (changes != null && changes.route.isOutermostTransaction())
            for (SyncProxy proxy : changes.tracked) proxy.dirty.clear();
    }
    @Override public void afterCompletion() {
        Changes changes = local.get();
        if (changes != null) changes.finished = true;
        local.remove();
    }

    private record Group(String source, Class<?> type) { }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void flush(Changes changes) {
        if (changes.flushing) return;
        changes.flushing = true;
        try {
            Map<Group, List<SyncProxy>> groups = new LinkedHashMap<>();
            for (SyncProxy proxy : changes.tracked)
                if (!proxy.dirty.isEmpty()) groups.computeIfAbsent(new Group(proxy.source, proxy.plan.type),
                        ignored -> new ArrayList<>()).add(proxy);
            for (var group : groups.entrySet()) {
                ExecuteRequest request = ExecuteRequest.ofUpdate(group.getKey().type());
                request.setSession(group.getKey().source());
                request.setCache(true); // Invalidate cached rows after automatic updates, too.
                List<SyncProxy> updated = new ArrayList<>();
                for (SyncProxy proxy : group.getValue()) {
                    IUpdateWrapper wrapper = changes.route.getWrapperFactory().createUpdateWrapper(
                            proxy.target, Map.copyOf(proxy.dirty), proxy.primaryKeys, proxy.versions);
                    if (wrapper == null) continue;
                    request.addRequest(wrapper);
                    updated.add(proxy);
                    if (request.getRoot() == null) request.setRoot(proxy.target);
                }
                if (!updated.isEmpty()) {
                    if (changes.springCompleted) throw new NoticeException("原始 Spring 事务已经结束，不能继续懒同步");
                    int affected = changes.route.update(request);
                    if (updated.stream().anyMatch(row -> !row.versions.isEmpty()) && affected != updated.size())
                        throw new NoticeException("懒同步乐观锁更新失败: " + group.getKey().type().getName());
                    for (SyncProxy proxy : updated) proxy.advanceVersions();
                }
                for (SyncProxy proxy : group.getValue()) proxy.dirty.clear();
            }
        } finally { changes.flushing = false; }
    }

    private final class SyncProxy implements MethodInterceptor {
        final Changes changes;
        final String source;
        final Object target, proxy;
        final Plan plan;
        final Map<String, Object> primaryKeys = new LinkedHashMap<>(), versions = new LinkedHashMap<>();
        final Map<String, Object> dirty = new LinkedHashMap<>();
        SyncProxy(Changes changes, String source, Object target) {
            this.changes = changes;
            this.source = Objects.requireNonNull(source, "session");
            this.target = target;
            this.plan = PLANS.get(ProxyUtil.getProxyClass(target.getClass()));
            for (Field field : plan.keys) {
                Object value = ReflectionUtil.invokeGetter(target, field.getName());
                if (value == null) throw new NoticeException("懒同步要求完整的非空主键: " + field.getName());
                primaryKeys.put(field.getName(), value);
            }
            for (Field field : plan.versions) {
                Object value = ReflectionUtil.invokeGetter(target, field.getName());
                if (!(value instanceof Number)) throw new NoticeException("懒同步版本字段必须是非空数字: " + field.getName());
                versions.put(field.getName(), value);
            }
            proxy = ProxyUtil.getProxyInstance(plan.type, this);
        }
        @Override public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Field field = args.length == 1 ? plan.setters.get(method.getName()) : null;
            // Only real property setters are tracked, not setUp()/setSomething(a,b).
            if (field != null && method.getParameterTypes()[0] != field.getType()) field = null;
            if (field == null) return methodProxy.invoke(target, args);
            changes.check();
            changes.joinSpring();
            if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                throw new NoticeException("只读事务不能执行懒同步更新");
            if (primaryKeys.containsKey(field.getName()) && !Objects.equals(primaryKeys.get(field.getName()), args[0]))
                throw new NoticeException("懒同步不允许修改主键: " + field.getName());
            if (versions.containsKey(field.getName()) && !Objects.equals(versions.get(field.getName()), args[0]))
                throw new NoticeException("懒同步版本字段由框架维护: " + field.getName());
            Object result = methodProxy.invoke(target, args); // Execute the business setter exactly once.
            if (!primaryKeys.containsKey(field.getName()) && !versions.containsKey(field.getName())) {
                Object value = ReflectionUtil.invokeGetter(target, field.getName());
                if (value == null) dirty.remove(field.getName());
                else dirty.put(field.getName(), value);
            }
            return result;
        }
        void advanceVersions() {
            for (var entry : versions.entrySet()) {
                Number old = (Number) entry.getValue();
                Object next;
                if (old instanceof Long) next = old.longValue() + 1;
                else if (old instanceof Integer) next = old.intValue() + 1;
                else throw new NoticeException("懒同步版本字段仅支持 Integer/int 或 Long/long");
                ReflectionUtil.invokeSetter(target, entry.getKey(), next);
                entry.setValue(next);
            }
        }
    }
}
