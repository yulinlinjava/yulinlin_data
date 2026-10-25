package com.yulinlin.data.core.session;

import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheMissException;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.cache.CacheNamespace;
import com.yulinlin.data.core.cache.CacheNamespaceResolver;
import com.yulinlin.data.core.cache.CachePartition;
import com.yulinlin.data.core.cache.CachePartitionResolver;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.OrmCacheKeyFactory;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.transaction.TransactionLog;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.wrapper.ICountWrapper;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractSelectWrapper;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.SegmentLock;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractSession extends LoadBalanceSession implements EntitySession {

    private static final Logger CACHE_LOG = LoggerFactory.getLogger("com.yulinlin.data.cache.QueryCache");

    private EntitySessionProperties sessionProperties = new EntitySessionProperties();

    private static final Logger EXECUTION_LOG = LoggerFactory.getLogger(AbstractSession.class);
    private volatile List<SessionExecutionListener> executionListeners = List.of();

    private IParseManager parseManager;

    private ICoderManager coderManager;

    private QueryCache cacheManager = NoOpQueryCache.INSTANCE;



    private EntityProxyService proxyService;


    /** 非 JDBC 后端的资源参与标记，不是业务事务上下文，也不表示具备服务端回滚能力。 */
    private final ThreadLocal<Boolean> resourceRollbackOnly = new ThreadLocal<>();

    /** Non-relational or externally managed sessions have no automatic table DDL by default. */
    @Override
    public List<String> createTableSql(Class<?> entityClass) {
        return List.of();
    }

    /** Non-relational or externally managed sessions require no relational schema initialization. */
    @Override
    public void initializeSchema(Collection<Class<?>> entityClasses) {
    }



    int core =  Runtime.getRuntime().availableProcessors();

    private ExecutorService threadPoolExecutor = new ThreadPoolExecutor(core,core*2,5,TimeUnit.MINUTES,new LinkedBlockingDeque<>());



    protected List bufferToBean(List<IDataBuffer> bufferList, Class clazz){



        List<Object> objects = coderManager.decodeObject(bufferList, clazz);


        return objects;
    }

    protected Object bufferToBean(IDataBuffer bufferList,Class clazz){
        return coderManager.decodeObject(bufferList,clazz);
    }







    protected   CompletableFuture<Integer> executeUpdateAsync( List<ParseResult> results,RequestType requestType){

        return CompletableFuture.supplyAsync(() -> executeUpdate(results,requestType),getThreadPoolExecutor());


    }

    protected abstract  Integer executeUpdate( List<ParseResult> list,RequestType requestType);

    protected  abstract  List<IDataBuffer> executeSelect(ParseResult request);

    protected  abstract  List<IDataBuffer> executeGroup(ParseResult request);


    protected  abstract  IDataBuffer executeCount(ParseResult request);

    /** 仅在请求实际访问后端时运行；是否开启事务由 RouteSession 决定。 */
    protected void beforeExecute(BaseRequest<?> request) { }

    /** @deprecated Execution groups are now bounded by parallelWriteGroupCount(), not a row chunk size. */
    @Deprecated
    public static int groupLen = 128;

    protected int parallelWriteGroupCount() {
        return 4;
    }

    protected  List<List<ParseResult>> parseNodesAndGroup(
            RequestType requestType,
            Object root, List<INode> nodes, Class clazz){

        List<ParseResult> results = parseNodes(requestType, root, nodes, clazz);
        if (!supportsParallelWrites() || results.isEmpty()) return List.of(results);
        int groups = Math.min(results.size(), Math.max(1, parallelWriteGroupCount()));
        List<List<ParseResult>> value = new ArrayList<>(groups);
        int baseSize = results.size() / groups;
        int remainder = results.size() % groups;
        int offset = 0;
        for (int i = 0; i < groups; i++) {
            int end = offset + baseSize + (i < remainder ? 1 : 0);
            value.add(results.subList(offset, end));
            offset = end;
        }
        return value;
    }


    protected boolean isMapUnderscoreToCamelCase() {
        return sessionProperties.isMapUnderscoreToCamelCase();
    }

    public void setSessionProperties(EntitySessionProperties sessionProperties) {
        this.sessionProperties = java.util.Objects.requireNonNull(sessionProperties, "sessionProperties");
    }

    public EntitySessionProperties getSessionProperties() {
        return sessionProperties;
    }

    /** Cache partition metadata must follow the same entity mapping and coder path as SQL parameters. */
    String cacheColumn(Class<?> sourceClass, String field) {
        return AliasContent.newInstance(sourceClass, isMapUnderscoreToCamelCase()).toColumn(field);
    }

    Object cachePartitionValue(Object value) {
        return coderManager.createEncoderBuffer().encode(value);
    }

    public static int batchSize = 5*512*1024;

    protected   <T extends CacheKey> List<ParseResult> parseNodes(

            RequestType requestType,
            Object root, List<INode> nodes, Class clazz){

        if(nodes.size() < batchSize){
            return nodes.stream().map(node -> {
                ParseResult result =parseNode(requestType,root,node,clazz);
                return result;
            }).collect(Collectors.toList());
        }else {
            return nodes.parallelStream().map(node -> {
                ParseResult result =parseNode(requestType,root,node,clazz);
                return result;
            }).collect(Collectors.toList());
        }



    }

    protected   ParseResult parseNode(

            RequestType requestType,
            Object root, INode node, Class clazz){
        SimpParamsContext context = createParamsContext(requestType, root, clazz);

        ParseResult result = (ParseResult) parseManager.parse(node,context);

        return result;
    }



    protected SimpParamsContext createParamsContext(RequestType type, Object root, Class<?> sourceClass) {
        return new SimpParamsContext(type, root, coderManager.createEncoderBuffer(),
                sourceClass, isMapUnderscoreToCamelCase());
    }

    protected ParseResult parseQueryNode(RequestType type, QueryRequest<?> request, INode node) {
        if (request.getIndexHint() == null) {
            // Preserve existing parser/session extension paths for every unhinted query.
            return parseNode(type, request.getRoot(), node, request.getFromClass());
        }
        request.getIndexHint().target(node);
        var context = createParamsContext(type, request.getRoot(), request.getFromClass());
        return (ParseResult) parseManager.parse(node, context, request.getIndexHint());
    }

    protected boolean isOpenAsync(ExecuteRequest request, List<ParseResult> results){
        // The threshold is checked against the whole request, not each worker's share.
        return threadPoolExecutor != null && request.isBatch() && !results.isEmpty()
                && supportsParallelWrites();
    }

    @SneakyThrows
    protected <E> E executeOperation(Callable<E> callable,ParseResult result){
        if (result != null) result.setTransactionId(TransactionLog.current().id());
        long started = System.nanoTime();

        E val;
        try {
            val = executeOperation(callable);
        }catch (Throwable e){
            completedExecution(result == null ? List.of() : List.of(result), started, null, e, false);
            throw e;
        }
        completedExecution(result == null ? List.of() : List.of(result), started, null, null, false);
        return val;

    }


    @SneakyThrows
    protected <E> E executeOperation(Callable<E> callable){
        try {
            return callable.call();
        }catch (Throwable e){
            // 只标记自己的资源；是否整体回滚由最外层 RouteSession 决定。
            try { setRollbackOnly(); }
            catch (Throwable cleanup) { if (e != cleanup) e.addSuppressed(cleanup); }
            throw e;
        }

    }

    @Override
    public <E> Integer count(QueryRequest<E> request) {
        ICountWrapper countWrapper = null;

        if(request.getWrapper() instanceof  ICountWrapper){
            countWrapper = (ICountWrapper)request.getWrapper();
        }else {
            countWrapper = new CountWrapper(request.getWrapper());
        }

        ParseResult result = parseQueryNode(RequestType.count, request, countWrapper);


        Supplier<Integer> dataLoader = () -> {
            try {

                return executeOperation(() -> {
                    beforeExecute(request);
                    IDataBuffer buffer = executeCount(result);
                    String value = buffer.getObject("total");
                    return Integer.parseInt(value);
                },result);

            } catch (Exception e) {
                throw e;
            }
        };


        Integer val = getCacheValue(request, result,dataLoader);

        return val;
    }




    @SneakyThrows

    public <E> Integer  execute(ExecuteRequest<E> request,RequestType requestType) {

        Integer val = executeOperation(() -> {
            // Decide on the owner thread, inside the framework transaction context.
            boolean parallel = request.isBatch() && threadPoolExecutor != null && supportsParallelWrites();
            List<List<ParseResult>> lists = parallel
                    ? parseNodesAndGroup(requestType, request.getRoot(), request.getWrappers(), request.getFromClass())
                    : List.of(parseNodes(requestType, request.getRoot(), request.getWrappers(), request.getFromClass()));
            int rows = lists.stream().mapToInt(List::size).sum();
            if (rows > 0) beforeExecute(request);
            int total = 0;
            Throwable failure = null;
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            String transactionId = TransactionLog.current().id();
            try {
                for (List<ParseResult> results : lists) {
                    if (results.isEmpty()) continue;
                    // 解析可能运行在 parallelStream，完成日志也可能在工作线程；统一由所属线程赋值。
                    for (ParseResult result : results) result.setTransactionId(transactionId);
                    long started = System.nanoTime();
                    if (parallel && isOpenAsync(request, results)) {
                        CompletableFuture<Integer> future = executeUpdateAsync(results, requestType)
                                .whenComplete((count, error) -> logBatch(
                                        results, started, count == null ? 0 : count, unwrapCompletion(error)));
                        futures.add(future);
                    } else {
                        try {
                            int affected = executeUpdate(results, requestType);
                            total += affected;
                            logBatch(results, started, affected, null);
                        } catch (Throwable error) {
                            logBatch(results, started, 0, error);
                            throw error;
                        }
                    }
                }
            } catch (Throwable error) {
                failure = error;
            }

            // Even after a synchronous/submission/task failure, drain EVERY submitted task.
            // join() deliberately does not abandon workers when the calling thread is interrupted.
            CompletableFuture.allOf(futures.stream()
                    .map(future -> future.handle((count, error) -> null))
                    .toArray(CompletableFuture[]::new)).join();
            for (CompletableFuture<Integer> future : futures) {
                try {
                    total += future.join();
                } catch (CompletionException error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    if (failure == null) failure = cause;
                    else if (failure != cause) failure.addSuppressed(cause);
                }
            }
            if (failure != null) {
                setRollbackOnly();
                if (failure instanceof Error error) throw error;
                if (failure instanceof Exception error) throw error;
                throw new IllegalStateException(failure);
            }
            return total;
        });

        return val;
    }

    final SegmentLock segmentLock = new SegmentLock();



    protected final <E> E getCacheValue(QueryRequest<?> request, ParseResult result, Supplier<E> callable ){

        CacheMode mode = request.getCacheMode();
        if (mode == CacheMode.NONE) return callable.get();

        boolean lockQuery = result.getType() == ParseType.select
                && request.getWrapper() instanceof AbstractSelectWrapper<?, ?, ?, ?> select
                && select.isLock();
        boolean transactionActive = isOpenTransaction();
        if (lockQuery || transactionActive) {
            if (mode == CacheMode.CACHE_ONLY) {
                throw new CacheMissException(lockQuery
                        ? "行锁查询不能从缓存获取，CACHE_ONLY 无法执行"
                        : "事务内查询不会访问全局缓存，CACHE_ONLY 无法执行");
            }
            cacheBypassDebug(result, lockQuery ? "lock-query" : "transaction");
            return callable.get();
        }

        if (!cacheManager.available()) {
            if (mode == CacheMode.CACHE_ONLY) {
                throw new CacheMissException("未配置查询缓存提供者，CACHE_ONLY 无法执行");
            }
            cacheBypassDebug(result, "no-provider");
            return callable.get();
        }

        CacheKey cacheKey = OrmCacheKeyFactory.query(
                group(), cluster(), getClass(), request.getEntityClass(),
                request.getFromClass(), result.getType(), request.getWrapper(), request.getIndexHint());
        Set<CacheNamespace> namespaces = CacheNamespaceResolver.resolve(
                getClass(), group(), request.getFromClass(), request.getWrapper(),
                request.getCacheNamespaces());
        Duration cacheTtl = cacheManager.resolveTtl(request.getCacheTtl(), namespaces);
        CachePartition partition = null;
        if (result.getType() == ParseType.select && namespaces.size() == 1) {
            CacheNamespace namespace = namespaces.iterator().next();
            partition = CachePartitionResolver.query(namespace, request.getWrapper(),
                    field -> cacheColumn(request.getFromClass(), field),
                    this::cachePartitionValue).orElse(null);
        }
        cacheKey = partition == null
                ? cacheManager.scope(cacheKey, namespaces, cacheTtl)
                : cacheManager.scopePartition(cacheKey, partition, cacheTtl);
        CacheValueType valueType = result.getType() == ParseType.count
                ? CacheValueType.scalar(Integer.class)
                : CacheValueType.listOf(request.getEntityClass());

        if (mode == CacheMode.REFRESH) {
            E value = callable.get();
            cacheManager.put(cacheKey, valueType, value, cacheTtl);
            cacheDebug("REFRESH", cacheKey, result);
            return value;
        }

        CacheLookup lookup = cacheManager.get(cacheKey, valueType);
        if (lookup.hit()) {
            cacheDebug("HIT", cacheKey, result);
            return (E) lookup.value();
        }
        cacheDebug("MISS", cacheKey, result);
        if (mode == CacheMode.CACHE_ONLY) {
            throw new CacheMissException("缓存未命中，CACHE_ONLY 不允许访问数据源: " + cacheKey.value());
        }

        Lock cacheLock = segmentLock.getLock(cacheKey);
        cacheLock.lock();
        try {
            lookup = cacheManager.get(cacheKey, valueType);
            if (lookup.hit()) {
                cacheDebug("HIT_AFTER_WAIT", cacheKey, result);
                return (E) lookup.value();
            }
            E value = callable.get();
            cacheManager.put(cacheKey, valueType, value, cacheTtl);
            cacheDebug("PUT", cacheKey, result);
            return value;
        } finally {
            cacheLock.unlock();
        }
    }



    @Override
    public <E> List<E> select(QueryRequest<E> request) {




            ParseResult result = parseQueryNode(RequestType.select, request, request.getWrapper());



        Supplier<List<E>> dataLoader = () -> {

                List<IDataBuffer> buffers = executeOperation(() -> {
                    beforeExecute(request);
                    return executeSelect(result);
                }, result);
                List<E>  data = bufferToBean(buffers, request.getEntityClass());


                return data;
        };

// 使用缓存封装方法
        return enhanceQueryResults(request, getCacheValue(request, result, dataLoader));


    }

    @SneakyThrows
    @Override
    public <E> Page<E> page(QueryRequest<E> request) {
        Integer count = count(request);
        List<E> select = select(request);
        return Page.of(select,count);
    }

    @Override
    public <E> Integer  insert(ExecuteRequest<E> request) {
        return execute(request,RequestType.insert);
    }

    @Override
    public <E> Integer  update(ExecuteRequest<E> request) {
        return execute(request,RequestType.update);
    }

    @Override
    public <E> Integer  delete(ExecuteRequest<E> request) {
        return execute(request,RequestType.delete);
    }

    @Override
    public <E> List<E> group(QueryRequest<E> request) {
        ParseResult result = parseQueryNode(RequestType.group, request, request.getWrapper());


        Supplier<List<E>> dataLoader = () -> {
            List<IDataBuffer> buffers  =    executeOperation(() -> {
                beforeExecute(request);
                return executeGroup(result);
            },result);
            List<E>  data = bufferToBean(buffers, request.getEntityClass());
            return data;

        };


       return enhanceQueryResults(request, getCacheValue(request, result, dataLoader));
    }

    private <E> List<E> enhanceQueryResults(QueryRequest<E> request, List<E> data) {
        // Never cache transaction-bound proxies, loaded relations, or caller-owned mutable entities.
        if (request.isCache()) data = ReflectionUtil.deepClone(data);
        data = proxyService.enhance(group(), request, data);
        return data;
    }

    public ICoderManager getCoderManager() {
        return coderManager;
    }

    protected IDataBuffer createDecoderBuffer(){
        return coderManager.createDecoderBuffer();
    }

    // 具体 Session 只处理自己的资源，不创建业务上下文，也不调用事务管理器。
    // 非 JDBC 后端仅维护参与标记，确保事务内绕过查询缓存；不承诺服务端回滚能力。
    @Override public void startTransaction() {
        if (resourceRollbackOnly.get() == null) resourceRollbackOnly.set(false);
    }
    @Override public void commitTransaction() { resourceRollbackOnly.remove(); }
    @Override public void rollbackTransaction() { resourceRollbackOnly.remove(); }
    @Override public boolean isOpenTransaction() { return resourceRollbackOnly.get() != null; }
    @Override public void setRollbackOnly() {
        if (isOpenTransaction()) resourceRollbackOnly.set(true);
    }
    @Override public boolean isRollbackOnly() { return Boolean.TRUE.equals(resourceRollbackOnly.get()); }

    private void logBatch(List<ParseResult> results, long started, int affected, Throwable error) {
        completedExecution(results, started, error == null ? affected : null, error, true);
    }

    /** 数据库模块直接打印自己的请求格式；无需注册日志 Bean。 */
    protected void printExecution(SessionExecution execution) {
    }

    private void completedExecution(List<ParseResult> results, long started, Integer affected,
                                    Throwable error, boolean batch) {
        SessionExecution execution = new SessionExecution(this, results,
                System.nanoTime() - started, affected, error, batch);
        try {
            printExecution(execution);
        } catch (RuntimeException failure) {
            reportObserverFailure("日志打印", failure);
        }
        for (SessionExecutionListener listener : executionListeners) {
            try {
                listener.afterExecution(execution);
            } catch (RuntimeException failure) {
                reportObserverFailure("执行监听器 " + listener.getClass().getName(), failure);
            }
        }
    }

    private static void reportObserverFailure(String observer, RuntimeException failure) {
        try {
            EXECUTION_LOG.warn("{}失败，已与数据源执行隔离", observer, failure);
        } catch (RuntimeException ignored) {
            // 日志后端异常也不能覆盖原始执行结果。
        }
    }

    /** 工厂初始化时传入可选监听器；空列表表示不启用监听。 */
    public void setExecutionListeners(List<? extends SessionExecutionListener> listeners) {
        executionListeners = listeners == null ? List.of() : List.copyOf(listeners);
    }

    private void cacheDebug(String event, CacheKey key, ParseResult result) {
        try {
            if (CACHE_LOG.isDebugEnabled()) {
                CACHE_LOG.debug("缓存访问 event={} group={} type={} key={}", event, group(),
                        result == null ? "unknown" : result.getType(), key == null ? "unknown" : key.value());
            }
        } catch (RuntimeException ignored) {
            // Cache observability must never change a query's result.
        }
    }

    private void cacheBypassDebug(ParseResult result, String reason) {
        try {
            if (CACHE_LOG.isDebugEnabled()) {
                CACHE_LOG.debug("缓存已跳过 group={} type={} reason={}", group(),
                        result == null ? "unknown" : result.getType(), reason);
            }
        } catch (RuntimeException ignored) {
            // Cache observability must never change a query's result.
        }
    }

    private static Throwable unwrapCompletion(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
    }

    public void setCoderManager(ICoderManager coderManager) {
        this.coderManager = coderManager;
    }





    public void setParseManager(IParseManager parseManager) {
        this.parseManager = parseManager;
    }

    public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
        this.threadPoolExecutor = threadPoolExecutor;
    }


    /** 仅由工厂初始化缓存提供者，不属于 EntitySession 的执行协议。 */
    public void setQueryCache(QueryCache cacheManager) {
        this.cacheManager = cacheManager == null ? NoOpQueryCache.INSTANCE : cacheManager;
    }

    /** @deprecated Use {@link #setQueryCache(QueryCache)}. */
    @Deprecated
    public void setCacheManager(QueryCache cacheManager) {
        setQueryCache(cacheManager);
    }

    public QueryCache getCacheManager() {
        return cacheManager;
    }

    public ExecutorService getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

    public IParseManager getParseManager() {
        return parseManager;
    }

    public EntityProxyService getProxyService() {
        return proxyService;
    }

    public void setProxyService(EntityProxyService proxyService) {
        this.proxyService = proxyService;
    }

    public void setCore(int core) {
        this.core = core;
    }



}
