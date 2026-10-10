package com.yulinlin.data.core.proxy;

import com.yulinlin.data.core.transaction.TransactionListener;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.DataProperties;
import com.yulinlin.data.core.session.SessionUtil;

import java.util.List;

public class EntityProxyService implements TransactionListener {

    private final EntityProxyFactory factory;
    private final DataProperties properties;
    private final ThreadLocal<Integer> autoUpdateDepth = ThreadLocal.withInitial(() -> 0);

    private EntityProxyService(EntityProxyFactory factory, DataProperties properties) {
        this.factory = factory;
        this.properties = properties;
    }

    public static EntityProxyService newInstance(){
        return newInstance(new DataProperties());
    }

    public static EntityProxyService newInstance(DataProperties properties) {
        return new EntityProxyService(new EntityProxyFactory(),
                properties == null ? new DataProperties() : properties);
    }

    public void enterAutoUpdate() {
        autoUpdateDepth.set(autoUpdateDepth.get() + 1);
    }

    public void exitAutoUpdate() {
        int depth = autoUpdateDepth.get();
        if (depth <= 1) autoUpdateDepth.remove();
        else autoUpdateDepth.set(depth - 1);
    }

    public boolean isAutoUpdate(QueryRequest<?> request) {
        Boolean override = request.getAutoUpdate();
        return override != null ? override : autoUpdateDepth.get() > 0 || properties.isAutoUpdate();
    }

    public boolean requiresTransaction(QueryRequest<?> request) {
        return isAutoUpdate(request) && factory.supportsAutoUpdate(request.getEntityClass());
    }

    public <E> List<E> enhance(String source, QueryRequest<E> request, List<E> data) {
        EntityProxyFactory.CacheContext cache = new EntityProxyFactory.CacheContext(
                request.getCacheMode(), request.getCacheTtl());
        return factory.enhance(source, data, isAutoUpdate(request), cache);
    }

    /**
     * 得到一个代理
     * @param data
     * @return
     */
   public   Object getLazyProxy(Object data){
       return factory.getLazyProxy(SessionUtil.nowSession(), data);
   }


   public <E> List<E> getLazyProxyList(List<E> data){
       return factory.getLazyProxyList(SessionUtil.nowSession(), data,
               EntityProxyFactory.CacheContext.DISABLED);
   }

    public   Object getSyncProxy(Object data){
        return factory.getManagedProxy(SessionUtil.nowSession(), data);
    }


    public List<Object> getSyncProxyList(List<?> data){
        return (List<Object>) (List<?>) factory.getManagedProxyList(SessionUtil.nowSession(), data,
                EntityProxyFactory.CacheContext.DISABLED);
    }

    @Override
    public void startTransaction() {
        factory.startTransaction();

    }

    @Override
    public void commitTransaction() {
        factory.commitTransaction();
    }

    @Override
    public void rollbackTransaction() {
        factory.rollbackTransaction();
    }

    @Override public void afterCompletion() { factory.afterCompletion(); }
}

