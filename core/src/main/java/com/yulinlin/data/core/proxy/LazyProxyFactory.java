package com.yulinlin.data.core.proxy;

import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.event.IProxyEvent;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.reflection.ProxyUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

/** One query result shares one thread-confined lazy association batch. */
public class LazyProxyFactory implements IProxyFactory {
    private final SyncProxyFactory syncProxyFactory;
    public record CacheContext(boolean enabled, Duration ttl) {
        private static final CacheContext DISABLED = new CacheContext(false, null);
        public CacheContext {
            if (!enabled) ttl = null;
        }
    }
    private static final ThreadLocal<CacheContext> CACHE =
            ThreadLocal.withInitial(() -> CacheContext.DISABLED);
    private static final ClassValue<Relations> RELATIONS = new ClassValue<>() {
        @Override protected Relations computeValue(Class<?> type) { return new Relations(type); }
    };
    private static final class Relations {
        final List<Field> fields = new ArrayList<>();
        final Map<String, Field> getters = new HashMap<>(), setters = new HashMap<>();
        Relations(Class<?> type) {
            for (Field field : ReflectionUtil.getAllDeclaredFields(type)) {
                JoinQuery query = AnnotationUtil.findAnnotation(field, JoinQuery.class);
                if (query == null) continue;
                if (query.batchSize() < 1) throw new NoticeException("JoinQuery.batchSize 必须大于 0: " + field);
                fields.add(field);
                if (AnnotationUtil.findAnnotation(field, JoinLazy.class) != null) {
                    String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
                    getters.put("get" + suffix, field);
                    getters.put("is" + suffix, field);
                    setters.put("set" + suffix, field);
                }
            }
        }
    }
    public LazyProxyFactory(SyncProxyFactory syncProxyFactory) { this.syncProxyFactory = syncProxyFactory; }
    public static void cache(boolean cache) { CACHE.set(new CacheContext(cache, null)); }
    public static void cache(CacheContext cache) { CACHE.set(cache == null ? CacheContext.DISABLED : cache); }
    public static CacheContext cacheContext() { return CACHE.get(); }
    public static boolean isCache() { return CACHE.get().enabled(); }
    public boolean isQuery(Class clazz) { return !RELATIONS.get(ProxyUtil.getProxyClass(clazz)).fields.isEmpty(); }
    @Override public Object getProxy(Object data) { return getLazyProxy(data); }
    public Object getLazyProxy(Object target) {
        if (target == null || target instanceof Map) return target;
        if (target instanceof List list) return getProxyList(list);
        return getProxyList(List.of(target)).getFirst();
    }
    @Override @SuppressWarnings("unchecked")
    public <E> List<E> getProxyList(List<E> list) {
        if (list == null || list.isEmpty() || list.getFirst() instanceof Map) return list;
        Class<?> type = ProxyUtil.getProxyClass(Objects.requireNonNull(list.getFirst(), "entity").getClass());
        Relations plan = RELATIONS.get(type);
        if (plan.fields.isEmpty()) return list;
        for (Object value : list) {
            if (value == null || ProxyUtil.getProxyClass(value.getClass()) != type)
                throw new NoticeException("关联查询列表必须包含同一种非空实体类型");
        }
        RouteSession route = Objects.requireNonNull(SessionUtil.route(), "Association loading requires a RouteSession");
        String source = SessionUtil.nowSession();
        CacheContext cache = cacheContext();
        List<?> beans = list;
        // Create proxies first so eager associations can navigate lazy dependencies.
        if (!plan.getters.isEmpty() && route.isOpenTransaction())
            beans = new LazyBatch(route, source, cache, plan, list).proxies;
        for (Field field : plan.fields)
            if (AnnotationUtil.findAnnotation(field, JoinLazy.class) == null) handleField(field, beans, cache, source);
        if (beans == list)
            for (Object bean : beans) if (bean instanceof IProxyEvent event) event.finishInjection();
        return (List<E>) beans;
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
        if (value instanceof Collection<?> collection)
            for (Object item : collection) flatten(item, into);
        else if (value.getClass().isArray())
            for (int i = 0; i < Array.getLength(value); i++) flatten(Array.get(value, i), into);
        else into.add(value);
    }
    private void initField(Field field, Object bean, Object result) {
        Object value = result;
        if (result instanceof List<?> list) {
            if (Set.class.isAssignableFrom(field.getType())) value = new LinkedHashSet<>(list);
            else if (!Collection.class.isAssignableFrom(field.getType())) value = list.isEmpty() ? null : list.getFirst();
        } else if (result instanceof Number number && (field.getType() == Long.class || field.getType() == long.class))
            value = number.longValue();
        if (value != null) {
            ReflectionUtil.invokeSetter(bean, field.getName(), value);
            if (bean instanceof IProxyEvent event) event.startInjection(field.getName(), value);
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void handleField(Field field, List<?> beans, CacheContext cache, String source) {
        if (beans.isEmpty()) return;
        JoinQuery query = AnnotationUtil.findAnnotation(field, JoinQuery.class);
        JoinSession explicit = AnnotationUtil.findAnnotation(field, JoinSession.class);
        String session = explicit == null ? source : explicit.value();
        Class<?> model = query.model();
        boolean count = model != Object.class;
        if (!count) model = Collection.class.isAssignableFrom(field.getType())
                ? GenericUtil.getFieldGeneric(field, 0) : field.getType();
        // Arbitrary per-parent predicates/counts retain their per-parent semantics.
        if (count || query.wheres().length > 0) {
            for (Object bean : beans) {
                BaseModelSelectWrapper select = select(session, model, query, cache, explicit);
                if (query.size() > 0) select.page(1, query.size());
                boolean hasCondition = false;
                if (query.wheres().length == 0) {
                    List<Object> values = keys(List.of(bean), query.value());
                    if (!values.isEmpty()) { select.in(query.primary(), values); hasCondition = true; }
                } else {
                    for (JoinWhere where : query.wheres()) {
                        List<Object> values = keys(List.of(bean), where.value());
                        if (values.isEmpty()) continue;
                        select.condition(where.name(), where.condition(),
                                where.condition() == ConditionEnum.in ? values : values.getFirst());
                        hasCondition = true;
                    }
                }
                if (hasCondition) initField(field, bean, count ? select.count() : enhance(field, session, select.selectList()));
                else initField(field, bean, count ? 0 : List.of());
            }
            return;
        }
        List<Object> ids = keys(beans, query.value());
        List<Object> results = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += query.batchSize()) {
            BaseModelSelectWrapper select = select(session, model, query, cache, explicit);
            select.in(query.primary(), ids.subList(start, Math.min(ids.size(), start + query.batchSize())));
            results.addAll(select.selectList());
        }
        if (ids.size() > query.batchSize() && query.order().length > 0) results.sort(order(query.order()));
        results = (List<Object>) enhance(field, session, results);
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
            for (Object id : keys(List.of(bean), query.value())) matches.addAll(byKey.getOrDefault(id, List.of()));
            matches.sort(Comparator.comparingInt(positions::get));
            initField(field, bean, matches);
        }
    }
    @SuppressWarnings("rawtypes")
    private BaseModelSelectWrapper select(String session, Class<?> model, JoinQuery query, CacheContext cache, JoinSession explicit) {
        BaseModelSelectWrapper select = new BaseModelSelectWrapper(session, model);
        if (cache.enabled()) {
            if (cache.ttl() == null) select.cache();
            else select.cache(cache.ttl());
        }
        if (explicit != null) select.getRequest().setCluster(explicit.cluster());
        for (JoinOrder order : query.order()) select.orderBy(order.name(), order.asc());
        return select;
    }
    private List<?> enhance(Field field, String session, List<?> results) {
        if (!results.isEmpty() && AnnotationUtil.findAnnotation(field, JoinSync.class) != null
                && SessionUtil.route().isOpenTransaction()) return syncProxyFactory.getProxyList(session, results);
        return results;
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
    private enum LoadState { LOADING, LOADED }
    private final class LazyBatch {
        final RouteSession route;
        final Object transaction;
        final Thread owner = Thread.currentThread();
        final String source;
        final CacheContext cache;
        final Relations plan;
        final List<?> targets;
        final List<Object> proxies = new ArrayList<>();
        final Map<Field, LoadState> states = new HashMap<>();
        final Map<Field, Set<Object>> assigned = new HashMap<>();
        LazyBatch(RouteSession route, String source, CacheContext cache, Relations plan, List<?> targets) {
            this.route = route;
            this.transaction = route.transactionIdentity();
            this.source = source;
            this.cache = cache;
            this.plan = plan;
            this.targets = List.copyOf(targets);
            for (Object target : targets) proxies.add(ProxyUtil.getProxyInstance(
                    ProxyUtil.getProxyClass(target.getClass()), new LazyProxy(target, this)));
        }
        void assigned(Field field, Object target) {
            assigned.computeIfAbsent(field, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(target);
        }
        void load(Field field, Object target) {
            if (states.get(field) == LoadState.LOADED || assigned.getOrDefault(field, Set.of()).contains(target)) return;
            if (Thread.currentThread() != owner || SessionUtil.route() != route || route.transactionIdentity() != transaction)
                throw new NoticeException("懒加载必须在创建代理的线程及原始事务内执行: " + field.getName());
            if (states.get(field) == LoadState.LOADING) throw new NoticeException("循环懒加载关联: " + field.getName());
            states.put(field, LoadState.LOADING);
            try {
                List<Object> pending = new ArrayList<>();
                for (int i = 0; i < targets.size(); i++) {
                    Object row = targets.get(i);
                    if (!assigned.getOrDefault(field, Set.of()).contains(row)
                            && ReflectionUtil.invokeGetter(row, field.getName()) == null) pending.add(proxies.get(i));
                }
                handleField(field, pending, cache, source);
                states.put(field, LoadState.LOADED); // Missing scalar results are loaded, too.
            } catch (RuntimeException | Error error) {
                states.remove(field);
                throw error;
            }
        }
    }
    private final class LazyProxy implements MethodInterceptor {
        private final Object target;
        private final LazyBatch batch;
        LazyProxy(Object target, LazyBatch batch) { this.target = target; this.batch = batch; }
        @Override public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            Field getter = args.length == 0 ? batch.plan.getters.get(method.getName()) : null;
            Object value = methodProxy.invoke(target, args);
            if (getter != null && value == null) {
                batch.load(getter, target);
                return methodProxy.invoke(target, args);
            }
            Field setter = args.length == 1 ? batch.plan.setters.get(method.getName()) : null;
            if (setter != null) batch.assigned(setter, target);
            return value;
        }
    }
}
