package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinSession;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheNamespaceResolver;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.CachePartitionResolver;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.queue.WriteQueueManager;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.core.transaction.TransactionContext;
import com.yulinlin.data.core.transaction.TransactionContext.CacheInvalidation;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.StringUtil;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAdder;

@Builder
@Slf4j
public class RouteSession extends RegisterSession implements TransactionSession {


    private EntityProxyService proxyService;


    @Builder.Default
    private IFilterManager filterManager = IFilterManager.NONE;

    @Builder.Default
    private TransactionListenerManager transactionListenerManager = new TransactionListenerManager();

    private WriteQueueManager writeQueueManager;

    /** 路由只用它协调写后失效，不再为具体 Session 注入缓存。 */
    @Builder.Default
    private QueryCache queryCache = NoOpQueryCache.INSTANCE;

    public void setQueryCache(QueryCache queryCache) {
        this.queryCache = queryCache == null ? NoOpQueryCache.INSTANCE : queryCache;
    }

    protected QueryCache queryCache() { return queryCache; }

    /** Selected cache provider's write-clear policy; individual requests may override it. */
    @Builder.Default
    private boolean clearOnWrite = true;

    private final ThreadLocal<Integer> annotatedSessionDepth = ThreadLocal.withInitial(() -> 0);

    public TransactionListenerManager getTransactionListenerManager() {
        return java.util.Objects.requireNonNull(transactionListenerManager, "transactionListenerManager");
    }

    @Override public void startTransaction() { getTransactionListenerManager().startTransaction(); }
    @Override public void commitTransaction() { getTransactionListenerManager().commitTransaction(); }
    @Override public void rollbackTransaction() { getTransactionListenerManager().rollbackTransaction(); }
    @Override public boolean isOpenTransaction() { return getTransactionListenerManager().isOpenTransaction(); }
    @Override public void setRollbackOnly() { getTransactionListenerManager().setRollbackOnly(); }
    @Override public boolean isRollbackOnly() { return getTransactionListenerManager().isRollbackOnly(); }
    protected int transactionDepth() { return getTransactionListenerManager().transactionDepth(); }
    public final Object transactionIdentity() { return TransactionListenerManager.currentContext(); }
    public final boolean isOutermostTransaction() { return transactionDepth() == 1; }

    @Override protected EntitySession enlist(EntitySession session) {
        return getTransactionListenerManager().enlist(session);
    }

    @Override protected EntitySession resolveSession(String group, JoinCluster cluster) {
        return getTransactionListenerManager().resolveSession(this, group + ":" + cluster.name(),
                () -> super.resolveSession(group, cluster));
    }

    @Override public void clear() { getTransactionListenerManager().clearSelections(this); }

    public void afterCommit(Runnable action) { getTransactionListenerManager().afterCommit(action); }

    private record PreparedRequest(EntitySession session, BaseRequest<?> request, int routeDepth) {
    }

    private void applyEntityRoute(BaseRequest request) {
        Class<?> fromClass = request.getFromClass();
        if (fromClass != null && fromClass != Object.class && StringUtil.isNull(request.getSession())
                && !hasAnnotatedSession()) {
            JoinSession joinDataSource = AnnotationUtil.findAnnotation(fromClass, JoinSession.class);
            if (joinDataSource != null) {
                request.setSession(joinDataSource.value());
                request.setCluster(joinDataSource.cluster());
            }
        }
    }

    /** 在调用线程固定后台写入的数据源组；复用实体注解、选库作用域和默认组规则。 */
    public String resolveWriteGroup(Class<?> fromClass, String group) {
        ExecuteRequest<?> request = ExecuteRequest.ofInsert(fromClass);
        request.setSession(group);
        applyEntityRoute(request);
        return session(request).group();
    }


    /** SQL、缓存 Key 和失效范围都只读取过滤后的请求；不通过 Request 标记猜测入栈次数。 */
    private PreparedRequest before(BaseRequest<?> original) {
        Objects.requireNonNull(original, "request");
        applyEntityRoute(original);
        EntitySession session = session(original);
        pushSession(session);
        int routeDepth = 1;
        try {
            BaseRequest<?> request = Objects.requireNonNull(filterManager.before(session.group(), original),
                    "before 过滤器不能返回 null 请求");
            if (original instanceof ExecuteRequest<?> write) {
                if (!(request instanceof ExecuteRequest<?> replacement))
                    throw new IllegalStateException("写入过滤器必须返回 ExecuteRequest");
                replacement.inheritExecutionOptions(write);
            } else if (original instanceof QueryRequest<?> query) {
                if (!(request instanceof QueryRequest<?> replacement))
                    throw new IllegalStateException("查询过滤器必须返回 QueryRequest");
                replacement.inheritExecutionOptions(query);
            } else {
                throw new IllegalArgumentException("不支持的请求类型: " + original.getClass().getName());
            }
            if (request.getSession() == null || request.getSession().isBlank()) request.setSession(session.group());
            if (request.getCluster() == null) request.setCluster(session.cluster());
            if (!Objects.equals(session.group(), request.getSession()) || session.cluster() != request.getCluster()) {
                session = session(request);
                pushSession(session);
                routeDepth++;
            }
            return new PreparedRequest(session, request, routeDepth);
        } catch (RuntimeException | Error error) {
            while (routeDepth-- > 0) popSession();
            throw error;
        }
    }

    private void release(PreparedRequest prepared) {
        for (int i = 0; i < prepared.routeDepth(); i++) popSession();
    }

    private Object after(EntitySession session, BaseRequest<?> request, Object data, Class<?> expectedType) {
        Object result = filterManager.after(session.group(), request, data);
        if (data != null && result == null && expectedType == Integer.class)
            throw new IllegalStateException("after 过滤器不能将计数或影响行数替换为 null");
        if (result != null && !expectedType.isInstance(result))
            throw new IllegalStateException("after 过滤器返回类型错误，预期 " + expectedType.getSimpleName()
                    + "，实际 " + result.getClass().getName());
        return result;
    }


    @SneakyThrows
    private  <E> E execute(BaseRequest request){
        ExecuteRequest<?> original = (ExecuteRequest<?>) request;
        if (original.isQueued() && isOpenTransaction()) {
            throw new NoticeException("Delayed write queue cannot be used inside an active transaction. 请在事务外调用 queue()。");
        }

        PreparedRequest prepared = before(original);

        try {
            ExecuteRequest<?> req = (ExecuteRequest<?>) prepared.request();
            if (req.isQueued()) {
                if (isOpenTransaction())
                    throw new NoticeException("延迟写队列必须在事务外调用，请在事务外调用 queue()。");
                if (prepared.session().isOpenTransaction()) {
                    throw new NoticeException("Delayed write queue cannot use a session with an active transaction. 请在事务外调用 queue()。");
                }
                if (writeQueueManager == null) {
                    throw new IllegalStateException("Write queue is not configured");
                }
                return (E) Integer.valueOf(writeQueueManager.submit(prepared.session(), req));
            }
            return (E) executeNow(prepared.session(), req, false);

        }finally {
            release(prepared);
        }

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Integer executeNow(EntitySession session, ExecuteRequest<?> request, boolean delayed) {
        return executeNow(session, request, delayed, false);
    }

    /**
     * 重放已完成业务加工的写节点。仅供内部数据同步等适配器使用：
     * 不执行 before/after，不补充业务默认值；仍参与路由事务、缓存失效及数据源日志。
     * 调用方必须持有独立节点快照，明确指定目标组；不能传入延迟队列请求。
     */
    public Integer replayWrite(String targetGroup, ExecuteRequest<?> request) {
        Objects.requireNonNull(request, "request");
        if (targetGroup == null || targetGroup.isBlank())
            throw new IllegalArgumentException("重放请求必须指定目标数据源");
        if (request.isQueued()) throw new IllegalArgumentException("重放请求不能再次进入延迟队列");
        if (request.getRequestType() != RequestType.insert && request.getRequestType() != RequestType.update
                && request.getRequestType() != RequestType.delete)
            throw new IllegalArgumentException("重放入口仅支持写请求");
        request.setSession(targetGroup);
        request.setCluster(JoinCluster.master);
        EntitySession target = session(targetGroup, JoinCluster.master);
        pushSession(target);
        try {
            return executeNow(target, request, false, true);
        } finally {
            popSession();
        }
    }

    /** 独立维护事务已经提交后，清理目标实体命名空间；不用于代替普通写请求失效流程。 */
    public void invalidateMaintenanceCache(EntitySession target, Class<?> entityClass) {
        if (!queryCache().available()) return;
        var namespaces = CacheNamespaceResolver.resolve(target.getClass(), target.group(), entityClass,
                List.of(), java.util.Set.of());
        queryCache().invalidate(namespaces);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Integer executeNow(EntitySession session, ExecuteRequest<?> request, boolean delayed, boolean replay) {
        if (!isOpenTransaction()) return transaction(() -> executeNow(session, request, delayed, replay));
        TransactionContext context = TransactionListenerManager.currentContext();
        try {
            enlist(session);
            CacheInvalidation invalidation = resolveInvalidation(session, request);
            Integer affected = switch (request.getRequestType()) {
                case insert -> session.insert((ExecuteRequest) request);
                case delete -> session.delete((ExecuteRequest) request);
                case update -> session.update((ExecuteRequest) request);
                default -> throw new IllegalArgumentException("Not a write request: " + request.getRequestType());
            };
            if (affected == null) return null;
            if (replay) {
                scheduleInvalidation(invalidation);
                return affected;
            }
            if (!delayed) {
                scheduleInvalidation(invalidation);
                return (Integer) after(session, request, affected, Integer.class);
            }
            try {
                scheduleInvalidation(invalidation);
            } catch (RuntimeException error) {
                log.error("延迟写执行成功，但查询缓存失效处理失败", error);
            }
            try {
                return (Integer) after(session, request, affected, Integer.class);
            } catch (RuntimeException error) {
                // 不能仅因通知失败而重试已经执行的 Node，避免计数、自增等业务重复写入。
                log.error("延迟写执行成功，但写后过滤器执行失败", error);
                return affected;
            }
        } catch (RuntimeException | Error error) {
            if (TransactionListenerManager.currentContext() == context) {
                try { setRollbackOnly(); }
                catch (Throwable cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            }
            throw error;
        }
    }

    private int executeQueued(EntitySession session, ExecuteRequest<?> request) {
        pushSession(session);
        try {
            return executeNow(session, request, true);
        } finally {
            popSession();
        }
    }

    public void setWriteQueueManager(WriteQueueManager writeQueueManager) {
        this.writeQueueManager = writeQueueManager;
        if (writeQueueManager != null) writeQueueManager.setExecutor(this::executeQueued);
    }

    public void setClearOnWrite(boolean clearOnWrite) {
        this.clearOnWrite = clearOnWrite;
    }

    private CacheInvalidation resolveInvalidation(EntitySession session, ExecuteRequest<?> request) {
        if (!queryCache().available() || !request.shouldClearCache(clearOnWrite)) return null;
        CacheInvalidation pending = new CacheInvalidation();
        pending.namespaces.addAll(CacheNamespaceResolver.resolve(
                session.getClass(), session.group(), request.getFromClass(),
                request.getWrappers(), request.getCacheNamespaces()));
        if (pending.namespaces.isEmpty()) {
            pending.all = true;
            log.warn("无法识别写操作的缓存命名空间，提交成功后将清理全部查询缓存");
        } else if (pending.namespaces.size() != 1 || request.getWrappers().isEmpty()) {
            pending.namespaces.forEach(pending::addPartitionTable);
        } else {
            CacheNamespace namespace = pending.namespaces.iterator().next();
            for (com.yulinlin.data.core.node.INode wrapper : request.getWrappers()) {
                CachePartition partition = session instanceof AbstractSession abstractSession
                        ? CachePartitionResolver.write(namespace, wrapper,
                                field -> abstractSession.cacheColumn(request.getFromClass(), field),
                                abstractSession::cachePartitionValue).orElse(null)
                        : CachePartitionResolver.write(namespace, wrapper).orElse(null);
                if (partition == null) {
                    pending.addPartitionTable(namespace);
                    break;
                }
                pending.addPartition(partition);
            }
        }
        return pending;
    }

    private void scheduleInvalidation(CacheInvalidation invalidation) {
        getTransactionListenerManager().invalidate(queryCache(), invalidation);
    }


    private static ThreadLocal<Map<Class,LongAdder>> mapThreadLocal = ThreadLocal.withInitial(() ->new HashMap<>());

    public static int deep = 6;

    @SuppressWarnings("unchecked")
    private <E> E executeList(QueryRequest<?> req, RequestType requestType) {
        // Keep the recursion guard for raw SQL, without requiring an entity or changing the request.
        Class<?> depthKey = req.getFromClass() == null ? Object.class : req.getFromClass();
        LongAdder depth = mapThreadLocal.get().computeIfAbsent(depthKey, ignored -> new LongAdder());
        if (depth.intValue() >= deep)
            throw new NoticeException("递归查询深度超过" + deep + ",请使用懒加载:"
                    + (depthKey == Object.class ? "自定义SQL" : depthKey.getName()));
        depth.increment();
        PreparedRequest prepared = null;
        try {
            prepared = before(req);
            QueryRequest<?> request = (QueryRequest<?>) prepared.request();
            if (requestType != RequestType.count && proxyService != null
                    && proxyService.requiresTransaction(request) && !isOpenTransaction())
                throw new NoticeException("Entity auto-update query requires an active transaction. 请开启事务。");
            EntitySession session = prepared.session();
            Object data = switch (requestType) {
                case select -> session.select((QueryRequest) request);
                case page -> session.page((QueryRequest) request);
                case group -> session.group((QueryRequest) request);
                case count -> session.count((QueryRequest) request);
                default -> throw new IllegalArgumentException("Not a query: " + requestType);
            };
            Class<?> expectedType = requestType == RequestType.page ? Page.class
                    : requestType == RequestType.count ? Integer.class : List.class;
            return (E) after(session, request, data, expectedType);
        } finally {
            depth.decrement();
            if (depth.intValue() == 0) mapThreadLocal.get().remove(depthKey);
            if (mapThreadLocal.get().isEmpty()) mapThreadLocal.remove();
            if (prepared != null) release(prepared);
        }
    }

    public <E> Integer insert(ExecuteRequest<E> request) {
        return execute(request);



    }


    public <E> Integer update(ExecuteRequest<E> request) {
        return execute(request);


    }


    public <E> Integer delete(ExecuteRequest<E> request) {
        return execute(request);


    }

    public <E> Page<E> page(QueryRequest<E> request) {
        return executeList(request,RequestType.page);
    }

    public <E> List<E> select(QueryRequest<E> request) {
        return executeList(request,RequestType.select);
    }


    public <E> Integer count(QueryRequest<E> request) {
        return executeList(request, RequestType.count);
    }


    public <E> List<E> group(QueryRequest<E> request) {
        return executeList(request,RequestType.group);


    }

    public  <E> E getLazyProxy(E data){
        return (E)proxyService.getLazyProxy(data);
   }


  public   <E>  List<E> getLazyProxy(List<E> data) {
      return (List<E>)proxyService.getLazyProxyList(data);
  }

    public  <E> E getSyncProxy(E data){
        return (E) proxyService.getSyncProxy(data);

    }

    @SneakyThrows
    public <E> E callable(String code, Callable<E> callable){
        pushSession(code);
        try {
            E val =  callable.call();
            return val;
        }finally {
            popSession();
        }

    }


    private EntitySession session(BaseRequest request){
        EntitySession session =  session(request.getSession(),request.getCluster());
        request.setSession(session.group());
        request.setCluster(session.cluster());
        return session;
    }

    public   <E> List<E> getSyncProxy(List<E> data){
        return (List<E>) proxyService.getSyncProxyList(data);
    }


    @SneakyThrows
    public <V> V transaction(Callable<V> callable){
        int previousDepth = transactionDepth();
        startTransaction();
        TransactionContext context = TransactionListenerManager.currentContext();
        try {
            V v =  callable.call();
            commitTransaction();
            return v;
        }catch (Throwable e){
            try {
                if (TransactionListenerManager.currentContext() == context && transactionDepth() > previousDepth) {
                    rollbackTransaction();
                }
            }
            catch (Throwable cleanup) { if (e != cleanup) e.addSuppressed(cleanup); }
            throw e;
        }
    }

    /** Enters a route selected by {@link JoinSession}; method-level routing outranks entity metadata. */
    public void pushAnnotatedSession(String code, JoinCluster cluster) {
        pushSession(code, cluster);
        annotatedSessionDepth.set(annotatedSessionDepth.get() + 1);
    }

    /** Leaves the most recent annotation-selected route and restores the enclosing route. */
    public void popAnnotatedSession() {
        int depth = annotatedSessionDepth.get();
        if (depth <= 0) throw new IllegalStateException("No @JoinSession route to leave");
        try {
            popSession();
        } finally {
            if (depth == 1) annotatedSessionDepth.remove();
            else annotatedSessionDepth.set(depth - 1);
        }
    }

    private boolean hasAnnotatedSession() {
        return annotatedSessionDepth.get() > 0;
    }



}
