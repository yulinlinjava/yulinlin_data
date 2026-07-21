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



        if(StringUtil.isNull(request.getSession())){
            JoinSession joinDataSource = AnnotationUtil.findAnnotation(request.getFromClass(),JoinSession.class);
            if(joinDataSource != null){
                request.setSession(joinDataSource.value());
                request.setCluster(joinDataSource.cluster());
            }
        }

        EntitySession session = session(request);
        pushSession(session);

        String name = session.group();
        request =  filterManager.before(name,request);
        //session被改写

        if(!name.equals(request.getSession())){
            session =  session(request);
            pushSession(session);
        }



        return session;
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


    private static ThreadLocal<LongAdder> threadLocal = ThreadLocal.withInitial(() ->new LongAdder());



    private static ThreadLocal<Map<Class,LongAdder>> mapThreadLocal = ThreadLocal.withInitial(() ->new HashMap<>());

    public static int deep = 6;

    private <E> E executeList(QueryRequest<?> req,RequestType requestType){
        LongAdder longAdder = mapThreadLocal.get().computeIfAbsent(req.getFromClass(), k -> new LongAdder());

        int val = longAdder.intValue();

        if(val >= deep){
            throw new NoticeException("递归查询深度超过6,请使用懒加载:"+req.getFromClass().getName());
        }


        longAdder.increment();
        EntitySession session = before(req);

        Object data = null;
        LongAdder adder = null;

        if(req.isCache()){
            adder = threadLocal.get();
            if(adder.intValue() == 0){
                LazyProxyFactory.cache(req.isCache());
            }
            adder.increment();;
        }


        try {
            switch (requestType) {
                case select: {
                    data = session.select((QueryRequest) req);
                    break;
                }
                case page: {
                    data = session.page((QueryRequest) req);
                    break;
                }
                case group: {
                    data = session.group((QueryRequest) req);
                    break;
                }
            }

            return (E)data;

        }finally {
            if(adder != null){
                adder.decrement();
            }
            longAdder.decrement();
            after(req);

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
        if(isOpenTransaction()){
            if(!session.isOpenTransaction()){
                session.startTransaction();
            }
        }
        return session;
    }

    public   <E> List<E> getSyncProxy(List<E> data){
        return (List<E>) proxyService.getSyncProxyList(data);
    }


    @SneakyThrows
    public <V> V transaction(Callable<V> callable){
        startTransaction();
        try {
            V v =  callable.call();
            commitTransaction();
            return v;
        }catch (Exception  e){
            rollbackTransaction();
            throw e;
        }
    }
    public void startTransaction(){
        super.startTransaction();
        transactionListenerManager.startTransaction();
    }

    public void commitTransaction(){
        transactionListenerManager.commitTransaction();
        super.commitTransaction();


    }

    public void rollbackTransaction(){
        transactionListenerManager.rollbackTransaction();
        super.rollbackTransaction();

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
