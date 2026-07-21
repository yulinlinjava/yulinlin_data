package com.yulinlin.data.core.session;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.wrapper.ICountWrapper;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.SegmentLock;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractSession extends LoadBalanceSession implements EntitySession {



    private LogManager logManager;

    private IParseManager parseManager;

    private ICoderManager coderManager;

    private DbCache cacheManager;



    private EntityProxyService proxyService;


    private IFilterManager filterManager;



    int core =  Runtime.getRuntime().availableProcessors();

    private ExecutorService threadPoolExecutor = new ThreadPoolExecutor(core,core*2,30,TimeUnit.MINUTES,new LinkedBlockingDeque<>());



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

  public   static int groupLen = 128;

    protected  List<List<ParseResult>> parseNodesAndGroup(
            RequestType requestType,
            Object root, List<INode> nodes, Class clazz){

         List<ParseResult> collect = parseNodes(requestType,root,nodes, clazz);

         if(collect .size() < groupLen){
             return Arrays.asList(collect);
         }

        int x = 0,y=0;

        List<List<ParseResult>> value = new ArrayList<>();


        while (true){
            x = y;
            y=x+groupLen;
            if(y>nodes.size()){
                y = nodes.size() ;
            }
            if(x >= y){
                break;
            }
            List<ParseResult> parseTypes = collect.subList(x, y);
            value.add(parseTypes);
        }



        return value;


    }


    protected abstract boolean isMapUnderscoreToCamelCase();

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
        boolean ok =  threadPoolExecutor != null && results.size() > request.getBatchSize();
        if(ok){

            return request.isBatch();
        }
        return false;

    }

    @SneakyThrows
    protected <E> E transaction(Callable<E> callable,ParseResult result){
        long x = System.currentTimeMillis();

        try {
            E val =  transaction(callable);

            logManager.success( System.currentTimeMillis() - x,result);
            return  val;
        }catch (Exception e){
            logManager.error( e,result);
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
        }catch (Exception e){
            if(!transaction){
                rollbackTransaction();
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

        List<List<ParseResult>> lists = parseNodesAndGroup(
                requestType,
                request.getRoot(),
                request.getWrappers(), request.getFromClass());


      Integer val =    transaction(() -> {

          int total = 0;
                ArrayList<CompletableFuture<Integer>> futures = null;


                for (List<ParseResult> results :lists) {


                        //执行异步任务
                        if(isOpenAsync(request,results)){
                            if(futures == null){
                                futures = new ArrayList<>();
                            }
                            long x = System.currentTimeMillis();
                            CompletableFuture<Integer> submit = executeUpdateAsync(results,requestType);

                            submit
                            .thenApply((res) -> {

                                    long time = (System.currentTimeMillis()-x) / results.size() ;
                                    for (ParseResult result : results) {
                                        logManager.success(time,result);
                                    }

                                return res;
                            })
                            .exceptionally(e -> {
                                for (ParseResult result : results) {
                                    logManager.error(e,result);
                                }
                                throw new RuntimeException(e);
                            });
                            futures.add(submit);
                        }else {

                            try{
                                long x = System.currentTimeMillis();
                                Integer data = executeUpdate(results, requestType);
                                long time = (System.currentTimeMillis()-x) / results.size() ;
                                for (ParseResult result : results) {
                                    logManager.success(time,result);
                                }

                                total+=data;
                            }catch (Exception e){
                                for (ParseResult result : results) {
                                    logManager.error(e,result);
                                }
                                throw e;
                            }

                        }

                }


                if(total > 0){
                    return total;
                }
                if(futures != null && futures.size() > 0){
                    for (CompletableFuture<Integer> future : futures) {
                        total+=future.get();
                    }
                }

              return total;

        });

        if(request.isCache()){
            for (INode node : request.getWrappers()) {
                CacheKey cacheKey = CacheKey.of(null, node);
                if(cacheKey.isSingleEqualsCondition()){
                    cacheManager.getCache(request.getEntityClass()).invalidate(
                            cacheKey.getKey()
                    );
                }else {
                    cacheManager.update(request.getEntityClass());
                }
            }

        }

        return val;
    }

    final SegmentLock segmentLock = new SegmentLock();



    private  <E> E getCacheValue(QueryRequest<?> request, ParseResult result, Supplier<E> callable ){

        E value = null;
        if(request.isCache()){

                CacheKey cacheKey =CacheKey.of(result.getType(), request.getWrapper());

                Integer key =cacheKey.getKey();

                value = cacheManager.get(request.getEntityClass(), cacheKey);
                if(value != null){
                    return value;
                }
                Lock cacheLock = segmentLock.getLock(key);

                cacheLock.lock();

                try {
                    //二次检查
                    value = cacheManager.get(request.getEntityClass(), cacheKey);
                    if(value != null){
                        return value;
                    }


                    value = callable.get();
                    if(value != null){
                        cacheManager.put(request.getEntityClass(), cacheKey,value);
                    }
                } finally {
                    cacheLock.unlock();
                }

        }else{
            value = callable.get();
        }
        return value;
    }



    @Override
    public <E> List<E> select(QueryRequest<E> request) {




            ParseResult result = parseNode(
                    RequestType.select,
                    request.getRoot(), request.getWrapper(),request.getFromClass());



        Supplier<List<E>> dataLoader = () -> {

                List<IDataBuffer> buffers = transaction(() -> executeSelect(result), result);
                List<E>  data = bufferToBean(buffers, request.getEntityClass());


                data =  proxyService.getLazyProxyList(data);
                filterManager.after(this.group(), request, data);
                return data;
        };

// 使用缓存封装方法
        return getCacheValue(request, result, dataLoader);


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
                return executeGroup(result);
            },result);
            List<E>  data = bufferToBean(buffers, request.getEntityClass());
            data =  proxyService.getLazyProxyList(data);
            filterManager.after(this.group(), request, data);
            return data;

        };


       return getCacheValue(request, result,dataLoader);
    }

    public ICoderManager getCoderManager() {
        return coderManager;
    }

    protected IDataBuffer createDecoderBuffer(){
        return coderManager.createDecoderBuffer();
    }

    private static ThreadLocal<LongAdder> transactionLocal = ThreadLocal.withInitial(() -> {
        return new LongAdder();
    });


    @Override
    public void startTransaction() {
        transactionLocal.get().increment();

    }

    @Override
    public void commitTransaction() {
        if(isOpenTransaction()){
            transactionLocal.get().decrement();
        }

    }

    @Override
    public void rollbackTransaction() {
        if(isOpenTransaction()){
            transactionLocal.get().decrement();
        }

    }

    @Override
    public boolean isOpenTransaction() {
        return  transactionLocal.get().intValue() > 0;
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


    public void setCacheManager(DbCache cacheManager) {
        this.cacheManager = cacheManager;
    }

    public DbCache getCacheManager() {
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
