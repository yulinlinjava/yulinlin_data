package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinSession;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.proxy.LazyProxyFactory;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;

@Builder
public class RouteSession  extends  RegisterSession{


    private EntityProxyService proxyService;


    private IFilterManager filterManager;

    private  TransactionListenerManager transactionListenerManager;


    private EntitySession before(BaseRequest request){



        Class<?> fromClass = request.getFromClass();
        if(fromClass != null && fromClass != Object.class && StringUtil.isNull(request.getSession())){
            JoinSession joinDataSource = AnnotationUtil.findAnnotation(fromClass,JoinSession.class);
            if(joinDataSource != null){
                request.setSession(joinDataSource.value());
                request.setCluster(joinDataSource.cluster());
            }
        }

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
    private void after(BaseRequest request){

        if(request.isSessionChange()){
            popSession();
        }
        popSession();
    }


    @SneakyThrows
    private  <E> E execute(BaseRequest request,RequestType requestType ){

        EntitySession session = before(request);

        try {
                ExecuteRequest req = (ExecuteRequest)request;
                Object call = null;
                if(requestType == RequestType.insert){
                    call =  session.insert(req);
                }else if(requestType == RequestType.delete){
                    call =  session.delete(req);
                }else if(requestType== RequestType.update){
                    call =  session.update(req);
                }

                if(call != null){
                    filterManager.after(session.group(),request,call);
                }
                return (E)call;

        }finally {
            after(request);
        }

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
        boolean previousCache = LazyProxyFactory.isCache();
        boolean entered = false;
        try {
            LazyProxyFactory.cache(previousCache || req.isCache());
            EntitySession session = before(req);
            entered = true;
            return (E) switch (requestType) {
                case select -> session.select((QueryRequest) req);
                case page -> session.page((QueryRequest) req);
                case group -> session.group((QueryRequest) req);
                default -> throw new IllegalArgumentException("Not a query: " + requestType);
            };
        } finally {
            LazyProxyFactory.cache(previousCache);
            depth.decrement();
            if (depth.intValue() == 0) mapThreadLocal.get().remove(depthKey);
            if (mapThreadLocal.get().isEmpty()) mapThreadLocal.remove();
            if (entered) after(req);
        }
    }

    public <E> Integer insert(ExecuteRequest<E> request) {
        return execute(request,RequestType.insert);



    }


    public <E> Integer update(ExecuteRequest<E> request) {
        return execute(request,RequestType.update);


    }


    public <E> Integer delete(ExecuteRequest<E> request) {
        return execute(request,RequestType.delete);


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
        Throwable failure = null;
        try {
            if (transactionListenerManager != null) {
                if (isRollbackOnly()) transactionListenerManager.rollbackTransaction();
                else transactionListenerManager.commitTransaction();
            }
            super.commitTransaction();
        } catch (Throwable error) {
            failure = error;
            if (isOpenTransaction()) {
                setRollbackOnly();
                try { if (transactionListenerManager != null) transactionListenerManager.rollbackTransaction(); }
                catch (Throwable cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
                try { super.rollbackTransaction(); }
                catch (Throwable cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
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
            try { if (transactionListenerManager != null) transactionListenerManager.afterCompletion(); }
            catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) throw failure;
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
