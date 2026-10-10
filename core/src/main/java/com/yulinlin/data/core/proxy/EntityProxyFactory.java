package com.yulinlin.data.core.proxy;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.event.IProxyEvent;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListener;
import com.yulinlin.data.core.wrapper.IUpdateWrapper;
import com.yulinlin.data.core.wrapper.factory.UpdatePatchFactory;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

/**
 * One CGLIB proxy per entity combines lazy association loading and transaction-scoped dirty tracking.
 * Metadata is parsed once per user class; per-object dirty arrays are allocated only after the first setter.
 */
public final class EntityProxyFactory implements IProxyFactory, TransactionListener {
    private static final Object UNSET = new Object();

    public record CacheContext(CacheMode mode, Duration ttl) {
        public static final CacheContext DISABLED = new CacheContext(CacheMode.NONE, null);

        public CacheContext {
            mode = mode == null ? CacheMode.NONE : mode;
            if (mode == CacheMode.NONE) ttl = null;
        }

        public boolean enabled() {
            return mode != CacheMode.NONE;
        }
    }

    private record SetterPlan(Field field, int index) { }

    private static final class Plan {
        final Class<?> type;
        final List<Field> relations = new ArrayList<>();
        final Map<String, Field> lazyGetters = new HashMap<>();
        final Map<String, Field> lazySetters = new HashMap<>();
        final Map<String, SetterPlan> persistentSetters = new HashMap<>();
        final List<Field> updateFields = new ArrayList<>();
        final List<Field> keys = new ArrayList<>();
        final List<Field> versions = new ArrayList<>();
        final boolean table;

        Plan(Class<?> type) {
            this.type = type;
            this.table = AnnotationUtil.findAnnotation(type, JoinTable.class) != null;
            for (Field field : ReflectionUtil.getAllDeclaredFields(type)) {
                JoinQuery query = AnnotationUtil.findAnnotation(field, JoinQuery.class);
                if (query != null) {
                    if (query.batchSize() < 1)
                        throw new NoticeException("JoinQuery.batchSize 必须大于 0: " + field);
                    relations.add(field);
                    if (AnnotationUtil.findAnnotation(field, JoinLazy.class) != null) {
                        String suffix = propertySuffix(field);
                        lazyGetters.put("get" + suffix, field);
                        lazyGetters.put("is" + suffix, field);
                        lazySetters.put("set" + suffix, field);
                    }
                }

                if (!UpdatePatchFactory.persistent(field)) continue;
                JoinField mapping = AnnotationUtil.findAnnotation(field, JoinField.class);
                JoinMeta meta = AnnotationUtil.findAnnotation(field, JoinMeta.class);
                boolean primary = meta != null && meta.primaryKey();
                boolean version = mapping != null && mapping.version() && mapping.update();
                if (primary) keys.add(field);
                if (version) {
                    if (primary) throw new NoticeException("主键不能同时作为版本字段: " + field);
                    if (field.getType() != Integer.class && field.getType() != int.class
                            && field.getType() != Long.class && field.getType() != long.class)
                        throw new NoticeException("自动更新版本字段仅支持 Integer/int 或 Long/long: " + field);
                    versions.add(field);
                }
                if (mapping == null || mapping.update() || primary) {
                    int index = updateFields.size();
                    updateFields.add(field);
                    persistentSetters.put("set" + propertySuffix(field), new SetterPlan(field, index));
                }
            }
        }

        boolean manageable() {
            return table && !keys.isEmpty();
        }

        boolean hasLazyRelations() {
            return !lazyGetters.isEmpty();
        }

        private static String propertySuffix(Field field) {
            String name = field.getName();
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }

    private static final ClassValue<Plan> PLANS = new ClassValue<>() {
        @Override
        protected Plan computeValue(Class<?> type) {
            return new Plan(type);
        }
    };

    private final ThreadLocal<Changes> local = new ThreadLocal<>();

    public boolean supportsAutoUpdate(Class<?> type) {
        return type != null && type != Object.class && !Map.class.isAssignableFrom(type)
                && PLANS.get(ProxyUtil.getProxyClass(type)).manageable();
    }

    public boolean hasRelations(Class<?> type) {
        return type != null && type != Object.class && !Map.class.isAssignableFrom(type)
                && !PLANS.get(ProxyUtil.getProxyClass(type)).relations.isEmpty();
    }

    @Override
    public <E> E getProxy(E data) {
        return getManagedProxy(SessionUtil.nowSession(), data);
    }

    @Override
    public <E> List<E> getProxyList(List<E> data) {
        return getManagedProxyList(SessionUtil.nowSession(), data, CacheContext.DISABLED);
    }

    public <E> E getManagedProxy(String source, E data) {
        if (data == null) return null;
        if (data instanceof List<?> list)
            return (E) getManagedProxyList(source, (List<Object>) list, CacheContext.DISABLED);
        List<E> values = getManagedProxyList(source, List.of(data), CacheContext.DISABLED);
        return values.getFirst();
    }

    public <E> List<E> getManagedProxyList(String source, List<E> data, CacheContext cache) {
        if (data == null || data.isEmpty()) return data;
        Class<?> type = userType(data.getFirst());
        Plan plan = PLANS.get(type);
        if (plan.table && plan.keys.isEmpty())
            throw new NoticeException("自动更新实体缺少JoinMeta(primaryKey = true): " + type.getName());
        return enhance(source, data, true, cache);
    }

    public <E> E getLazyProxy(String source, E data) {
        if (data == null) return null;
        if (data instanceof List<?> list)
            return (E) getLazyProxyList(source, (List<Object>) list, CacheContext.DISABLED);
        List<E> values = enhance(source, List.of(data), false, CacheContext.DISABLED);
        return values.getFirst();
    }

    public <E> List<E> getLazyProxyList(String source, List<E> data, CacheContext cache) {
        return enhance(source, data, false, cache);
    }

    @SuppressWarnings("unchecked")
    public <E> List<E> enhance(String source, List<E> data, boolean autoUpdate, CacheContext cache) {
        if (data == null || data.isEmpty() || data.getFirst() == null || data.getFirst() instanceof Map) return data;
        Class<?> type = userType(data.getFirst());
        Plan plan = PLANS.get(type);
        for (Object value : data) {
            if (value == null || userType(value) != type)
                throw new NoticeException("关联查询列表必须包含同一种非空实体类型");
        }

        RouteSession route = Objects.requireNonNull(SessionUtil.route(), "Entity proxy requires a RouteSession");
        boolean managed = autoUpdate && plan.manageable();
        if (managed && !route.isOpenTransaction()) throw transactionRequired();
        boolean lazy = plan.hasLazyRelations() && route.isOpenTransaction();
        List<?> beans = data;
        LazyBatch batch = null;
        if (managed || lazy) {
            batch = new LazyBatch(route, source, cache, autoUpdate, plan, data);
            beans = batch.proxies;
        }

        for (Field field : plan.relations) {
            if (AnnotationUtil.findAnnotation(field, JoinLazy.class) == null)
                handleField(field, beans, cache, source, autoUpdate);
        }
        for (Object bean : beans) if (bean instanceof IProxyEvent event) event.finishInjection();
        return (List<E>) beans;
    }

    private static NoticeException transactionRequired() {
        return new NoticeException("Entity auto-update query requires an active transaction. 请开启事务。");
    }

    private Class<?> userType(Object value) {
        return ProxyUtil.getProxyClass(value instanceof Class<?> type ? type : value.getClass());
    }

    private final class Changes {
        final RouteSession route;
        final Object transaction;
        final Thread owner = Thread.currentThread();
        final Map<String, IdentityHashMap<Object, EntityProxy>> sources = new LinkedHashMap<>();
        final List<EntityProxy> tracked = new ArrayList<>();
        boolean flushing;
        boolean finished;
        boolean springRegistered;
        boolean springCompleted;

        Changes(RouteSession route) {
            this.route = route;
            this.transaction = route.transactionIdentity();
        }

        void check() {
            if (finished || springCompleted || Thread.currentThread() != owner || SessionUtil.route() != route
                    || route.transactionIdentity() != transaction)
                throw new NoticeException("自动更新 setter 必须在创建代理的线程及原始事务内执行");
        }

        void joinSpring() {
            if (springRegistered || !TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isSynchronizationActive()) return;
            springRegistered = true;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    if (finished || route.transactionIdentity() != transaction) return;
                    if (readOnly && tracked.stream().anyMatch(EntityProxy::hasDirty))
                        throw new NoticeException("只读事务不能执行自动更新");
                    if (!route.isRollbackOnly()) EntityProxyFactory.this.flush(Changes.this);
                }

                @Override
                public void afterCompletion(int status) {
                    springCompleted = true;
                    if (status != STATUS_COMMITTED && route.transactionIdentity() == transaction)
                        route.setRollbackOnly();
                }
            });
        }
    }

    private Changes changes() {
        RouteSession route = SessionUtil.route();
        if (route == null || !route.isOpenTransaction()) throw transactionRequired();
        Changes changes = local.get();
        if (changes == null || changes.finished || changes.transaction != route.transactionIdentity()
                || changes.route != route) {
            changes = new Changes(route);
            local.set(changes);
        }
        changes.check();
        changes.joinSpring();
        return changes;
    }

    @Override
    public void startTransaction() {
        changes();
    }

    @Override
    public void commitTransaction() {
        Changes changes = local.get();
        if (changes != null && !changes.finished && changes.route.isOutermostTransaction()) flush(changes);
    }

    @Override
    public void rollbackTransaction() {
        Changes changes = local.get();
        if (changes != null && changes.route.isOutermostTransaction())
            for (EntityProxy proxy : changes.tracked) proxy.clearDirty();
    }

    @Override
    public void afterCompletion() {
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
            Map<Group, List<EntityProxy>> groups = new LinkedHashMap<>();
            for (EntityProxy proxy : changes.tracked) {
                if (proxy.hasDirty()) groups.computeIfAbsent(new Group(proxy.source, proxy.plan.type),
                        ignored -> new ArrayList<>()).add(proxy);
            }
            for (var group : groups.entrySet()) {
                ExecuteRequest request = ExecuteRequest.ofUpdate(group.getKey().type());
                request.setSession(group.getKey().source());
                request.invalidate();
                List<EntityProxy> updated = new ArrayList<>();
                for (EntityProxy proxy : group.getValue()) {
                    IUpdateWrapper wrapper = changes.route.getWrapperFactory().createUpdateWrapper(
                            proxy.target, proxy.dirtyValues(), proxy.primaryKeys, proxy.versions);
                    if (wrapper == null) continue;
                    request.addRequest(wrapper);
                    updated.add(proxy);
                    if (request.getRoot() == null) request.setRoot(proxy.target);
                }
                if (!updated.isEmpty()) {
                    if (changes.springCompleted)
                        throw new NoticeException("原始 Spring 事务已经结束，不能继续自动更新");
                    int affected = changes.route.update(request);
                    if (updated.stream().anyMatch(row -> !row.versions.isEmpty()) && affected != updated.size())
                        throw new NoticeException("自动更新乐观锁失败: " + group.getKey().type().getName());
                    for (EntityProxy proxy : updated) proxy.advanceVersions();
                }
                for (EntityProxy proxy : group.getValue()) proxy.clearDirty();
            }
        } finally {
            changes.flushing = false;
        }
    }

    private EntityProxy proxy(Changes changes, String source, Object value, boolean managed, LazyBatch batch) {
        IdentityHashMap<Object, EntityProxy> cache = changes.sources.computeIfAbsent(source,
                ignored -> new IdentityHashMap<>());
        EntityProxy existing = cache.get(value);
        if (existing != null) {
            if (managed) existing.enableManaged();
            existing.attach(batch);
            return existing;
        }
        EntityProxy created = new EntityProxy(changes, source, value, managed, batch);
        cache.put(value, created);
        cache.put(created.proxy, created);
        changes.tracked.add(created);
        return created;
    }

    private final class EntityProxy implements MethodInterceptor {
        final Changes changes;
        final String source;
        final Object target;
        final Object proxy;
        final Plan plan;
        final Map<String, Object> primaryKeys = new LinkedHashMap<>();
        final Map<String, Object> versions = new LinkedHashMap<>();
        boolean managed;
        LazyBatch batch;
        BitSet dirty;
        Object[] dirtyValues;
        Object[] originals;

        EntityProxy(Changes changes, String source, Object target, boolean managed, LazyBatch batch) {
            this.changes = changes;
            this.source = Objects.requireNonNull(source, "session");
            this.target = target;
            this.plan = PLANS.get(userType(target));
            this.managed = managed;
            this.batch = batch;
            if (managed) initializeManagedState();
            this.proxy = ProxyUtil.getProxyInstance(plan.type, this);
        }

        void attach(LazyBatch batch) {
            if (batch != null && this.batch == null) this.batch = batch;
        }

        void enableManaged() {
            if (managed) return;
            managed = true;
            initializeManagedState();
        }

        void initializeManagedState() {
            if (!plan.manageable())
                throw new NoticeException("自动更新实体缺少JoinTable或JoinMeta(primaryKey = true): " + plan.type.getName());
            if (!primaryKeys.isEmpty()) return;
            for (Field field : plan.keys) {
                Object value = ReflectionUtil.invokeGetter(target, field.getName());
                if (value == null) throw new NoticeException("自动更新要求完整的非空主键: " + field.getName());
                primaryKeys.put(field.getName(), value);
            }
            for (Field field : plan.versions) {
                Object value = ReflectionUtil.invokeGetter(target, field.getName());
                if (!(value instanceof Number))
                    throw new NoticeException("自动更新版本字段必须是非空数字: " + field.getName());
                versions.put(field.getName(), value);
            }
        }

        @Override
        public Object intercept(Object ignored, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Field lazyGetter = args.length == 0 && batch != null ? plan.lazyGetters.get(method.getName()) : null;
            if (lazyGetter != null) {
                Object value = methodProxy.invoke(target, args);
                if (value == null) {
                    batch.load(lazyGetter, target);
                    return methodProxy.invoke(target, args);
                }
                return value;
            }

            Field lazySetter = args.length == 1 && batch != null ? plan.lazySetters.get(method.getName()) : null;
            SetterPlan setter = args.length == 1 ? plan.persistentSetters.get(method.getName()) : null;
            if (setter != null && method.getParameterTypes()[0] != setter.field().getType()) setter = null;
            if (!managed || setter == null) {
                Object result = methodProxy.invoke(target, args);
                if (lazySetter != null) batch.assigned(lazySetter, target);
                return result;
            }

            changes.check();
            changes.joinSpring();
            if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                throw new NoticeException("只读事务不能执行自动更新");
            String name = setter.field().getName();
            if (primaryKeys.containsKey(name) && !Objects.equals(primaryKeys.get(name), args[0]))
                throw new NoticeException("自动更新不允许修改主键: " + name);
            if (versions.containsKey(name) && !Objects.equals(versions.get(name), args[0]))
                throw new NoticeException("自动更新版本字段由框架维护: " + name);

            int index = setter.index();
            Object before = ReflectionUtil.invokeGetter(target, name);
            ensureDirtyState();
            if (originals[index] == UNSET) originals[index] = before;
            Object result = methodProxy.invoke(target, args);
            Object value = ReflectionUtil.invokeGetter(target, name);
            if (value == null || Objects.equals(originals[index], value)) {
                dirty.clear(index);
                dirtyValues[index] = null;
            } else {
                dirty.set(index);
                dirtyValues[index] = value;
            }
            if (lazySetter != null) batch.assigned(lazySetter, target);
            return result;
        }

        private void ensureDirtyState() {
            if (dirty != null) return;
            dirty = new BitSet(plan.updateFields.size());
            dirtyValues = new Object[plan.updateFields.size()];
            originals = new Object[plan.updateFields.size()];
            Arrays.fill(originals, UNSET);
        }

        boolean hasDirty() {
            return dirty != null && !dirty.isEmpty();
        }

        Map<String, Object> dirtyValues() {
            Map<String, Object> values = new LinkedHashMap<>();
            if (dirty == null) return values;
            for (int index = dirty.nextSetBit(0); index >= 0; index = dirty.nextSetBit(index + 1))
                values.put(plan.updateFields.get(index).getName(), dirtyValues[index]);
            return values;
        }

        void clearDirty() {
            if (dirty == null) return;
            dirty.clear();
            Arrays.fill(dirtyValues, null);
            Arrays.fill(originals, UNSET);
        }

        void advanceVersions() {
            for (var entry : versions.entrySet()) {
                Number old = (Number) entry.getValue();
                Object next;
                if (old instanceof Long) next = old.longValue() + 1;
                else if (old instanceof Integer) next = old.intValue() + 1;
                else throw new NoticeException("自动更新版本字段仅支持 Integer/int 或 Long/long");
                ReflectionUtil.invokeSetter(target, entry.getKey(), next);
                entry.setValue(next);
            }
        }
    }

    private enum LoadState { LOADING, LOADED }

    private final class LazyBatch {
        final RouteSession route;
        final Object transaction;
        final Thread owner = Thread.currentThread();
        final String source;
        final CacheContext cache;
        final boolean autoUpdate;
        final Plan plan;
        final List<Object> targets = new ArrayList<>();
        final List<Object> proxies = new ArrayList<>();
        final Map<Field, LoadState> states = new HashMap<>();
        final Map<Field, Set<Object>> assigned = new HashMap<>();

        LazyBatch(RouteSession route, String source, CacheContext cache, boolean autoUpdate,
                  Plan plan, List<?> values) {
            this.route = route;
            this.transaction = route.transactionIdentity();
            this.source = source;
            this.cache = cache == null ? CacheContext.DISABLED : cache;
            this.autoUpdate = autoUpdate;
            this.plan = plan;
            Changes changes = changes();
            for (Object value : values) {
                EntityProxy handler = proxy(changes, source, value, autoUpdate && plan.manageable(), this);
                targets.add(handler.target);
                proxies.add(handler.proxy);
            }
        }

        void assigned(Field field, Object target) {
            assigned.computeIfAbsent(field,
                    ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(target);
        }

        void load(Field field, Object target) {
            if (states.get(field) == LoadState.LOADED
                    || assigned.getOrDefault(field, Set.of()).contains(target)) return;
            if (Thread.currentThread() != owner || SessionUtil.route() != route
                    || route.transactionIdentity() != transaction)
                throw new NoticeException("懒加载必须在创建代理的线程及原始事务内执行: " + field.getName());
            if (states.get(field) == LoadState.LOADING)
                throw new NoticeException("循环懒加载关联: " + field.getName());
            states.put(field, LoadState.LOADING);
            try {
                List<Object> pending = new ArrayList<>();
                Set<Object> fieldAssignments = assigned.getOrDefault(field, Set.of());
                for (int i = 0; i < targets.size(); i++) {
                    Object row = targets.get(i);
                    if (!fieldAssignments.contains(row)
                            && ReflectionUtil.invokeGetter(row, field.getName()) == null)
                        pending.add(proxies.get(i));
                }
                handleField(field, pending, cache, source, autoUpdate);
                states.put(field, LoadState.LOADED);
            } catch (RuntimeException | Error error) {
                states.remove(field);
                throw error;
            }
        }
    }

    private List<Object> keys(Collection<?> beans, String expression) {
        if (!expression.startsWith("${")) return List.of(expression);
        if (!expression.endsWith("}") || expression.length() < 4)
            throw new NoticeException("非法关联字段表达式: " + expression);
        Collection<?> values = beans;
        for (String part : expression.substring(2, expression.length() - 1).split("\\.")) {
            List<Object> next = new ArrayList<>();
            for (Object value : values) flatten(ReflectionUtil.invokeGetter(value, part), next);
            values = next;
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private void flatten(Object value, Collection<Object> into) {
        if (value == null) return;
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) flatten(item, into);
        } else if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) flatten(Array.get(value, i), into);
        } else into.add(value);
    }

    private void initField(Field field, Object bean, Object result) {
        Object value = result;
        if (result instanceof List<?> list) {
            if (Set.class.isAssignableFrom(field.getType())) value = new LinkedHashSet<>(list);
            else if (!Collection.class.isAssignableFrom(field.getType()))
                value = list.isEmpty() ? null : list.getFirst();
        } else if (result instanceof Number number
                && (field.getType() == Long.class || field.getType() == long.class)) {
            value = number.longValue();
        }
        if (value != null) {
            ReflectionUtil.invokeSetter(bean, field.getName(), value);
            if (bean instanceof IProxyEvent event) event.startInjection(field.getName(), value);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void handleField(Field field, List<?> beans, CacheContext cache, String source,
                             boolean autoUpdate) {
        if (beans.isEmpty()) return;
        JoinQuery query = AnnotationUtil.findAnnotation(field, JoinQuery.class);
        JoinSession explicit = AnnotationUtil.findAnnotation(field, JoinSession.class);
        String session = explicit == null ? source : explicit.value();
        Class<?> model = query.model();
        boolean count = model != Object.class;
        if (!count) model = Collection.class.isAssignableFrom(field.getType())
                ? GenericUtil.getFieldGeneric(field, 0) : field.getType();

        if (count || query.wheres().length > 0) {
            for (Object bean : beans) {
                BaseModelSelectWrapper select = select(session, model, query, cache, explicit, autoUpdate);
                if (query.size() > 0) select.page(1, query.size());
                boolean hasCondition = false;
                if (query.wheres().length == 0) {
                    List<Object> values = keys(List.of(bean), query.value());
                    if (!values.isEmpty()) {
                        select.in(query.primary(), values);
                        hasCondition = true;
                    }
                } else {
                    for (JoinWhere where : query.wheres()) {
                        List<Object> values = keys(List.of(bean), where.value());
                        if (values.isEmpty()) continue;
                        select.condition(where.name(), where.condition(),
                                where.condition() == ConditionEnum.in ? values : values.getFirst());
                        hasCondition = true;
                    }
                }
                if (hasCondition) initField(field, bean, count ? select.count() : select.selectList());
                else initField(field, bean, count ? 0 : List.of());
            }
            return;
        }

        List<Object> ids = keys(beans, query.value());
        List<Object> results = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += query.batchSize()) {
            BaseModelSelectWrapper select = select(session, model, query, cache, explicit, autoUpdate);
            select.in(query.primary(), ids.subList(start, Math.min(ids.size(), start + query.batchSize())));
            results.addAll(select.selectList());
        }
        if (ids.size() > query.batchSize() && query.order().length > 0) results.sort(order(query.order()));
        Map<Object, List<Object>> byKey = new HashMap<>();
        Map<Object, Integer> positions = new IdentityHashMap<>();
        for (int i = 0; i < results.size(); i++) {
            Object result = results.get(i);
            Object id = ReflectionUtil.invokeGetter(result, query.primary());
            byKey.computeIfAbsent(id, ignored -> new ArrayList<>()).add(result);
            positions.put(result, i);
        }
        for (Object bean : beans) {
            List<Object> matches = new ArrayList<>();
            for (Object id : keys(List.of(bean), query.value()))
                matches.addAll(byKey.getOrDefault(id, List.of()));
            matches.sort(Comparator.comparingInt(positions::get));
            initField(field, bean, matches);
        }
    }

    @SuppressWarnings("rawtypes")
    private BaseModelSelectWrapper select(String session, Class<?> model, JoinQuery query,
                                          CacheContext cache, JoinSession explicit, boolean autoUpdate) {
        BaseModelSelectWrapper select = new BaseModelSelectWrapper(session, model).autoUpdate(autoUpdate);
        if (cache != null && cache.enabled()) {
            if (cache.ttl() == null) select.cache(cache.mode());
            else select.cache(cache.mode(), cache.ttl());
        }
        if (explicit != null) select.getRequest().setCluster(explicit.cluster());
        for (JoinOrder order : query.order()) select.orderBy(order.name(), order.asc());
        return select;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Comparator<Object> order(JoinOrder[] orders) {
        Comparator<Object> comparator = (left, right) -> 0;
        for (JoinOrder order : orders) {
            Comparator<Object> next = Comparator.comparing(
                    bean -> sortValue(ReflectionUtil.invokeGetter(bean, order.name())),
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            comparator = comparator.thenComparing(order.asc() ? next : next.reversed());
        }
        return comparator;
    }

    @SuppressWarnings("rawtypes")
    private Comparable sortValue(Object value) {
        if (value == null) return null;
        if (value instanceof DateTime date) return date.toLocalDateTime();
        if (value instanceof Enum<?> enumeration) return enumeration.name();
        if (value instanceof Comparable comparable) return comparable;
        throw new NoticeException("跨批次关联排序字段必须支持自然排序: " + value.getClass().getName());
    }
}
