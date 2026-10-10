package com.yulinlin.data.core.session;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.CacheMissException;
import com.yulinlin.data.core.cache.CacheMode;
import com.yulinlin.data.core.cache.CacheNamespaceResolver;
import com.yulinlin.data.core.cache.CacheLookup;
import com.yulinlin.data.core.cache.CacheValueType;
import com.yulinlin.data.core.cache.NoOpQueryCache;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.wrapper.ICountWrapper;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.SegmentLock;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractSession extends LoadBalanceSession implements EntitySession {

    private EntitySessionProperties sessionProperties = new EntitySessionProperties();

    private LogManager logManager;

    private IParseManager parseManager;

    private ICoderManager coderManager;

    private QueryCache cacheManager = NoOpQueryCache.INSTANCE;



    private EntityProxyService proxyService;


    private IFilterManager filterManager;

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

    /** Runs inside the request transaction, only when the request actually reaches the backend. */
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
        SimpParamsContext context = new SimpParamsContext(requestType,root,coderManager.createEncoderBuffer(),clazz,isMapUnderscoreToCamelCase());

        ParseResult result = (ParseResult) parseManager.parse(node,context);

        return result;
    }



    protected boolean isOpenAsync(ExecuteRequest request, List<ParseResult> results){
        // The threshold is checked against the whole request, not each worker's share.
        return threadPoolExecutor != null && request.isBatch() && !results.isEmpty()
                && supportsParallelWrites();
    }

    @SneakyThrows
    protected <E> E transaction(Callable<E> callable,ParseResult result){
        long x = System.currentTimeMillis();

        try {
            E val =  transaction(callable);

            logManager.success(System.currentTimeMillis() - x, result, this);
            return  val;
        }catch (Exception e){
            logManager.error(e, result, this);
            throw e;
        }

    }


    @SneakyThrows
    protected <E> E transaction( Callable<E> callable){



        boolean transaction = isOpenTransaction();
        if(!transaction){
            startTransaction();
        }

        try {
            E call = callable.call();
            if(!transaction){
                commitTransaction();
            }

            return call;
        }catch (Throwable e){
            setRollbackOnly();
            if(!transaction){
                try { rollbackTransaction(); }
                catch (Throwable rollbackFailure) { e.addSuppressed(rollbackFailure); }
            }
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

        ParseResult result = parseNode(
                RequestType.count,
                request.getRoot(),countWrapper,request.getFromClass());


        Supplier<Integer> dataLoader = () -> {
            try {

                return transaction(() -> {
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

        Integer val = transaction(() -> {
            // Decide on the owner thread, inside the actual transaction context (including Spring).
            boolean parallel = request.isBatch() && threadPoolExecutor != null && supportsParallelWrites();
            List<List<ParseResult>> lists = parallel
                    ? parseNodesAndGroup(requestType, request.getRoot(), request.getWrappers(), request.getFromClass())
                    : List.of(parseNodes(requestType, request.getRoot(), request.getWrappers(), request.getFromClass()));
            int rows = lists.stream().mapToInt(List::size).sum();
            if (rows > 0) beforeExecute(request);
            if (rows < Math.max(1, request.getBatchSize())) {
                parallel = false;
                if (lists.size() > 1) {
                    List<ParseResult> combined = new ArrayList<>(rows);
                    lists.forEach(combined::addAll);
                    lists = List.of(combined);
                }
            }
            int total = 0;
            Throwable failure = null;
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            try {
                for (List<ParseResult> results : lists) {
                    if (results.isEmpty()) continue;
                    long started = System.currentTimeMillis();
                    if (parallel && isOpenAsync(request, results)) {
                        CompletableFuture<Integer> future = executeUpdateAsync(results, requestType)
                                .whenComplete((count, error) -> logBatch(results, started, error));
                        futures.add(future);
                    } else {
                        try {
                            total += executeUpdate(results, requestType);
                            logBatch(results, started, null);
                        } catch (Throwable error) {
                            logBatch(results, started, error);
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

        CacheKey cacheKey = CacheKey.query(
                group(), cluster(), getClass(), request.getEntityClass(),
                request.getFromClass(), result.getType(), request.getWrapper());
        cacheKey = cacheManager.scope(cacheKey, CacheNamespaceResolver.resolve(
                getClass(), group(), request.getFromClass(), request.getWrapper(),
                request.getCacheNamespaces()), request.getCacheTtl());
        CacheValueType valueType = result.getType() == ParseType.count
                ? CacheValueType.scalar(Integer.class)
                : CacheValueType.listOf(request.getEntityClass());

        if (mode == CacheMode.REFRESH) {
            E value = callable.get();
            cacheManager.put(cacheKey, valueType, value, request.getCacheTtl());
            return value;
        }

        CacheLookup lookup = cacheManager.get(cacheKey, valueType);
        if (lookup.hit()) return (E) lookup.value();
        if (mode == CacheMode.CACHE_ONLY) {
            throw new CacheMissException("缓存未命中，CACHE_ONLY 不允许访问数据源: " + cacheKey.value());
        }

        Lock cacheLock = segmentLock.getLock(cacheKey);
        cacheLock.lock();
        try {
            lookup = cacheManager.get(cacheKey, valueType);
            if (lookup.hit()) return (E) lookup.value();
            E value = callable.get();
            cacheManager.put(cacheKey, valueType, value, request.getCacheTtl());
            return value;
        } finally {
            cacheLock.unlock();
        }
    }



    @Override
    public <E> List<E> select(QueryRequest<E> request) {




            ParseResult result = parseNode(
                    RequestType.select,
                    request.getRoot(), request.getWrapper(),request.getFromClass());



        Supplier<List<E>> dataLoader = () -> {

                List<IDataBuffer> buffers = transaction(() -> {
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
        ParseResult result = parseNode(
                RequestType.group,
                request.getRoot(), request.getWrapper(),request.getFromClass());


        Supplier<List<E>> dataLoader = () -> {
            List<IDataBuffer> buffers  =    transaction(() -> {
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
        filterManager.after(this.group(), request, data);
        return data;
    }

    public ICoderManager getCoderManager() {
        return coderManager;
    }

    protected IDataBuffer createDecoderBuffer(){
        return coderManager.createDecoderBuffer();
    }

    private final TransactionScope transactions = new TransactionScope();

    @Override public void startTransaction() { transactions.start(); }
    @Override public void commitTransaction() { transactions.finish(false); }
    @Override public void rollbackTransaction() { transactions.finish(true); }
    @Override public boolean isOpenTransaction() { return transactions.isOpen(); }
    @Override public void setRollbackOnly() { transactions.setRollbackOnly(); }
    @Override public boolean isRollbackOnly() { return transactions.isRollbackOnly(); }

    private void logBatch(List<ParseResult> results, long started, Throwable error) {
        if (logManager == null) return;
        long time = (System.currentTimeMillis() - started) / results.size();
        for (ParseResult result : results) {
            if (error == null) logManager.success(time, result, this);
            else logManager.error(error, result, this);
        }
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


    @Override
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

    public LogManager getLogManager() {
        return logManager;
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

    public IFilterManager getFilterManager() {
        return filterManager;
    }

    public void setFilterManager(IFilterManager filterManager) {
        this.filterManager = filterManager;
    }

    public void setCore(int core) {
        this.core = core;
    }


    public void setLogManager(LogManager logManager) {
        this.logManager = logManager;
    }


}
