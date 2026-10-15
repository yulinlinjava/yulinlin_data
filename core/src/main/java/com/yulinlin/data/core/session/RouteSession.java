package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.anno.JoinSession;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheNamespaceResolver;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.CachePartitionResolver;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.queue.WriteQueueManager;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.StringUtil;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;

@Builder
@Slf4j
public class RouteSession  extends  RegisterSession{


    private EntityProxyService proxyService;


    private IFilterManager filterManager;

    private  TransactionListenerManager transactionListenerManager;

    private WriteQueueManager writeQueueManager;

    /** Selected cache provider's write-clear policy; individual requests may override it. */
    @Builder.Default
    private boolean clearOnWrite = true;

    private final ThreadLocal<PendingInvalidation> pendingInvalidation =
            ThreadLocal.withInitial(PendingInvalidation::new);
    private final ThreadLocal<Integer> annotatedSessionDepth = ThreadLocal.withInitial(() -> 0);
    private final Object springInvalidationResource = new Object();

    private static final class PendingInvalidation {
        boolean all;
        final Set<CacheNamespace> namespaces = new LinkedHashSet<>();
        final Set<CachePartition> partitions = new LinkedHashSet<>();
        final Set<CacheNamespace> partitionTables = new LinkedHashSet<>();

        void addPartition(CachePartition partition) {
            if (!partitionTables.contains(partition.namespace())) partitions.add(partition);
        }

        void addPartitionTable(CacheNamespace namespace) {
            partitionTables.add(namespace);
            partitions.removeIf(partition -> partition.namespace().equals(namespace));
        }

        void merge(PendingInvalidation other) {
            all |= other.all;
            if (all) {
                namespaces.clear();
                partitions.clear();
                partitionTables.clear();
                return;
            }
            namespaces.addAll(other.namespaces);
            other.partitionTables.forEach(this::addPartitionTable);
            other.partitions.forEach(this::addPartition);
        }
        boolean empty() { return !all && namespaces.isEmpty(); }
    }

    private record PreparedWrite(EntitySession session, ExecuteRequest<?> request, int routeDepth) {
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


    private EntitySession before(BaseRequest request){
        applyEntityRoute(request);

        EntitySession session = session(request);
        pushSession(session);

        try {
            String name = session.group();
            request = filterManager.before(name, request);
            if (!name.equals(request.getSession())) {
                session = session(request);
                pushSession(session);
            }
            return session;
        } catch (RuntimeException | Error error) {
            popSession(); // A failed filter must not leave its source on the route stack.
            throw error;
        }
    }

    private PreparedWrite beforeWrite(ExecuteRequest<?> original) {
        applyEntityRoute(original);
        EntitySession session = session(original);
        pushSession(session);
        int routeDepth = 1;
        try {
            String name = session.group();
            BaseRequest<?> filtered = filterManager.before(name, original);
            if (!(filtered instanceof ExecuteRequest<?> request)) {
                throw new IllegalStateException("A write filter must return ExecuteRequest");
            }
            request.inheritExecutionOptions(original);
            if (!name.equals(request.getSession())) {
                session = session(request);
                pushSession(session);
                routeDepth++;
            }
            return new PreparedWrite(session, request, routeDepth);
        } catch (RuntimeException | Error error) {
            while (routeDepth-- > 0) popSession();
            throw error;
        }
    }

    private void after(PreparedWrite prepared) {
        for (int i = 0; i < prepared.routeDepth(); i++) popSession();
    }
    private void after(BaseRequest request){

        if(request.isSessionChange()){
            popSession();
        }
        popSession();
    }


    @SneakyThrows
    private  <E> E execute(BaseRequest request){
        ExecuteRequest<?> original = (ExecuteRequest<?>) request;
        if (original.isQueued() && (isOpenTransaction()
                || TransactionSynchronizationManager.isActualTransactionActive())) {
            throw new NoticeException("Delayed write queue cannot be used inside an active transaction. 请在事务外调用 queue()。");
        }

        PreparedWrite prepared = beforeWrite(original);

        try {
            ExecuteRequest<?> req = prepared.request();
            if (req.isQueued()) {
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
            after(prepared);
        }

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Integer executeNow(EntitySession session, ExecuteRequest<?> request, boolean delayed) {
        PendingInvalidation invalidation = resolveInvalidation(session, request);
        Integer affected = switch (request.getRequestType()) {
            case insert -> session.insert((ExecuteRequest) request);
            case delete -> session.delete((ExecuteRequest) request);
            case update -> session.update((ExecuteRequest) request);
            default -> throw new IllegalArgumentException("Not a write request: " + request.getRequestType());
        };

        if (affected != null) {
            if (delayed) {
                try {
                    scheduleInvalidation(invalidation);
                } catch (RuntimeException error) {
                    log.error("延迟写执行成功，但查询缓存失效处理失败", error);
                }
                try {
                    filterManager.after(session.group(), request, affected);
                } catch (RuntimeException error) {
                    log.error("延迟写执行成功，但写后过滤器执行失败", error);
                }
            } else {
                scheduleInvalidation(invalidation);
                filterManager.after(session.group(), request, affected);
            }
        }
        return affected;
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

    private PendingInvalidation resolveInvalidation(EntitySession session, ExecuteRequest<?> request) {
        if (!queryCache().available() || !request.shouldClearCache(clearOnWrite)) return null;
        PendingInvalidation pending = new PendingInvalidation();
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

    private void scheduleInvalidation(PendingInvalidation invalidation) {
        if (invalidation == null || invalidation.empty()) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            PendingInvalidation springPending = (PendingInvalidation)
                    TransactionSynchronizationManager.getResource(springInvalidationResource);
            if (springPending == null) {
                springPending = new PendingInvalidation();
                TransactionSynchronizationManager.bindResource(springInvalidationResource, springPending);
                PendingInvalidation registered = springPending;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { applyInvalidation(registered); }
                    @Override public void afterCompletion(int status) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(springInvalidationResource);
                    }
                });
            }
            springPending.merge(invalidation);
        } else if (isOpenTransaction()) {
            pendingInvalidation.get().merge(invalidation);
        } else {
            applyInvalidation(invalidation);
        }
    }

    private void applyPendingInvalidation() {
        PendingInvalidation pending = pendingInvalidation.get();
        pendingInvalidation.remove();
        applyInvalidation(pending);
    }

    private void discardPendingInvalidation() {
        pendingInvalidation.remove();
    }

    private void applyInvalidation(PendingInvalidation invalidation) {
        if (invalidation == null || invalidation.empty()) return;
        if (invalidation.all) queryCache().invalidateAll();
        else {
            queryCache().invalidate(Set.copyOf(invalidation.namespaces));
            queryCache().invalidatePartitionTables(Set.copyOf(invalidation.partitionTables));
            queryCache().invalidatePartitions(Set.copyOf(invalidation.partitions));
        }
    }


    private static ThreadLocal<Map<Class,LongAdder>> mapThreadLocal = ThreadLocal.withInitial(() ->new HashMap<>());

    public static int deep = 6;

    @SuppressWarnings("unchecked")
    private <E> E executeList(QueryRequest<?> req, RequestType requestType) {
        if (proxyService != null && proxyService.requiresTransaction(req) && !isOpenTransaction())
            throw new NoticeException("Entity auto-update query requires an active transaction. 请开启事务。");
        // Keep the recursion guard for raw SQL, without requiring an entity or changing the request.
        Class<?> depthKey = req.getFromClass() == null ? Object.class : req.getFromClass();
        LongAdder depth = mapThreadLocal.get().computeIfAbsent(depthKey, ignored -> new LongAdder());
        if (depth.intValue() >= deep)
            throw new NoticeException("递归查询深度超过" + deep + ",请使用懒加载:"
                    + (depthKey == Object.class ? "自定义SQL" : depthKey.getName()));
        depth.increment();
        boolean entered = false;
        try {
            EntitySession session = before(req);
            entered = true;
            return (E) switch (requestType) {
                case select -> session.select((QueryRequest) req);
                case page -> session.page((QueryRequest) req);
                case group -> session.group((QueryRequest) req);
                default -> throw new IllegalArgumentException("Not a query: " + requestType);
            };
        } finally {
            depth.decrement();
            if (depth.intValue() == 0) mapThreadLocal.get().remove(depthKey);
            if (mapThreadLocal.get().isEmpty()) mapThreadLocal.remove();
            if (entered) after(req);
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
        EntitySession session = before(request);

        try {
            Integer    call =  session.count(request);

            return call;

        }finally {
            after(request);
        }

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
        try {
            V v =  callable.call();
            commitTransaction();
            return v;
        }catch (Throwable e){
            setRollbackOnly();
            try { if (transactionDepth() > previousDepth) rollbackTransaction(); }
            catch (Throwable cleanup) { if (e != cleanup) e.addSuppressed(cleanup); }
            throw e;
        }
    }
    @SneakyThrows
    public void startTransaction(){
        if (!isOpenTransaction()) discardPendingInvalidation();
        super.startTransaction();
        try {
            if (transactionListenerManager != null) transactionListenerManager.startTransaction();
        } catch (Throwable error) {
            try { rollbackTransaction(); }
            catch (Throwable cleanup) { if (error != cleanup) error.addSuppressed(cleanup); }
            throw error;
        }
    }

    @SneakyThrows
    public void commitTransaction() {
        if (!isOpenTransaction()) return;
        if (transactionDepth() > 1) {
            try {
                if (transactionListenerManager != null) {
                    if (isRollbackOnly()) transactionListenerManager.rollbackTransaction();
                    else transactionListenerManager.commitTransaction();
                }
            }
            catch (Throwable error) {
                try { rollbackTransaction(); }
                catch (Throwable cleanup) { if (error != cleanup) error.addSuppressed(cleanup); }
                throw error;
            }
            super.commitTransaction();
            return;
        }
        boolean commitRequested = !isRollbackOnly();
        boolean dataFinished = false;
        Throwable failure = null;
        try {
            if (transactionListenerManager != null) {
                if (isRollbackOnly()) transactionListenerManager.rollbackTransaction();
                else transactionListenerManager.commitTransaction();
            }
            super.commitTransaction();
            dataFinished = true;
            if (commitRequested) applyPendingInvalidation();
            else discardPendingInvalidation();
        } catch (Throwable error) {
            failure = error;
            if (isOpenTransaction()) {
                setRollbackOnly();
                try { if (transactionListenerManager != null) transactionListenerManager.rollbackTransaction(); }
                catch (Throwable cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
                try { super.rollbackTransaction(); }
                catch (Throwable cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
            }
            if (!dataFinished) {
                // A participant may already have committed before another one failed.  We cannot
                // prove that nothing changed, so a harmless cache miss is safer than stale data.
                try {
                    if (commitRequested) applyPendingInvalidation();
                    else discardPendingInvalidation();
                } catch (Throwable cleanup) {
                    if (failure != cleanup) failure.addSuppressed(cleanup);
                }
            }
        } finally {
            try { if (transactionListenerManager != null) transactionListenerManager.afterCompletion(); }
            catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) throw failure;
    }

    @SneakyThrows
    public void rollbackTransaction() {
        if (!isOpenTransaction()) return;
        if (transactionDepth() > 1) {
            try { if (transactionListenerManager != null) transactionListenerManager.rollbackTransaction(); }
            finally { super.rollbackTransaction(); }
            return;
        }
        Throwable failure = null;
        try { if (transactionListenerManager != null) transactionListenerManager.rollbackTransaction(); }
        catch (Throwable error) { failure = error; }
        try { super.rollbackTransaction(); }
        catch (Throwable cleanup) {
            if (failure == null) failure = cleanup;
            else if (failure != cleanup) failure.addSuppressed(cleanup);
        } finally {
            discardPendingInvalidation();
            try { if (transactionListenerManager != null) transactionListenerManager.afterCompletion(); }
            catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) throw failure;
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
/*

    @Override
    public EntitySession session(String code, JoinCluster tag) {
        EntitySession session =  super.session(code, tag);
        if(isOpenTransaction()){
            if(!session.isOpenTransaction()){
                session.startTransaction();
            }
        }
        return session;
    }

    public  <E> E session(String code, Supplier<E> supplier){
        return session(code,JoinCluster.master,supplier);
    }
    public  <E> E session(String code,JoinCluster tag, Supplier<E> supplier){

        pushSession(code,tag);
        try {
            return supplier.get();
        }finally {
            popSession();

        }
    }
*/



}
