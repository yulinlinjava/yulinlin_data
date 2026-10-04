package com.yulinlin.data.core.proxy;

import com.yulinlin.data.core.transaction.TransactionListener;

import java.util.List;
import java.util.stream.Collectors;

public class EntityProxyService implements TransactionListener {

    private LazyProxyFactory lazyFactory;

    private SyncProxyFactory syncFactory;

    private EntityProxyService(LazyProxyFactory lazyFactory, SyncProxyFactory syncFactory) {
        this.lazyFactory = lazyFactory;
        this.syncFactory = syncFactory;
    }

    public static EntityProxyService newInstance(){
        SyncProxyFactory syncProxyFactory = new SyncProxyFactory();
        LazyProxyFactory lazyProxyFactory = new LazyProxyFactory(syncProxyFactory);
        return new EntityProxyService(lazyProxyFactory,syncProxyFactory);
    }

    /**
     * 得到一个代理
     * @param data
     * @return
     */
   public   Object getLazyProxy(Object data){
       return lazyFactory.getProxy(data);
   }


   public <E> List<E> getLazyProxyList(List<E> data){
       return lazyFactory.getProxyList(data);
   }

    public   Object getSyncProxy(Object data){
        return syncFactory.getProxy(data);
    }


    public List<Object> getSyncProxyList(List<?> data){
        return data.stream().map(this::getSyncProxy).collect(Collectors.toList());
    }

    @Override
    public void startTransaction() {
        syncFactory.startTransaction();

    }

    @Override
    public void commitTransaction() {
        syncFactory.commitTransaction();
    }

    @Override
    public void rollbackTransaction() {
        syncFactory.rollbackTransaction();
    }
}

