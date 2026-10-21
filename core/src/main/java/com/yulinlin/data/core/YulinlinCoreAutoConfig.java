package com.yulinlin.data.core;

import com.yulinlin.data.core.aop.JoinSessionAop;
import com.yulinlin.data.core.aop.JoinSyncAop;
import com.yulinlin.data.core.aop.JoinTransactionAop;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.CacheRegistry;
import com.yulinlin.data.core.filter.IFilter;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.filter.InitFilter;
import com.yulinlin.data.core.filter.SimpFilterManager;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.loadbalan.LoadBalanceProperties;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.queue.WriteQueueManager;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.DataProperties;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListener;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.core.wrapper.IWrapperFactory;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.data.lang.util.ThreadUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;


@AutoConfiguration
@EnableConfigurationProperties({LoadBalanceProperties.class, DataProperties.class})
public class YulinlinCoreAutoConfig {

    @ConditionalOnMissingBean
    @Bean
    public IWrapperFactory wrapperFactory() {
        WrapperFactoryImpl wrapperFactory = WrapperFactoryImpl.newInstance(
                InsertWrapper.class,
                UpdateWrapper.class,
                DeleteWrapper.class,
                SelectWrapper.class,
                GroupWrapper.class
        );
        return wrapperFactory;
    }


    @ConditionalOnMissingBean
    @Bean
    public JoinSessionAop dataSourceAop(){
        JoinSessionAop loadBalance =   new JoinSessionAop();
        return loadBalance;
    }

    @ConditionalOnMissingBean
    @Bean
    public LoadBalance loadBalance(LoadBalanceProperties properties){
        RandomLoadBalance loadBalance =   new RandomLoadBalance();
        loadBalance.setDefaultGroup(properties.getDefaultGroup());
        loadBalance.heartbeat(300);
        return loadBalance;
    }


    @ConditionalOnMissingBean
    @Bean
    public JoinTransactionAop joinTransactionAop(){
        return  new JoinTransactionAop();
    }


    @ConditionalOnMissingBean
    @Bean
    public IFilterManager filterManager(ObjectProvider<IFilter> filters){
        SimpFilterManager manager = new SimpFilterManager(filters.orderedStream().toList());
        return manager;
    }

    @ConditionalOnMissingBean
    @Bean
    public EntityProxyService entityProxyService(DataProperties properties){
        EntityProxyService manager =EntityProxyService.newInstance(properties);
        return manager;
    }

    @ConditionalOnMissingBean
    @Bean
    public JoinSyncAop joinSyncAop(EntityProxyService proxyService) {
        return new JoinSyncAop(proxyService);
    }

    @ConditionalOnMissingBean
    @Bean
    public TransactionListenerManager transactionListenerManager(ObjectProvider<TransactionListener> listeners,
                                                                 DataProperties properties){
        TransactionListenerManager manager = new TransactionListenerManager(listeners.orderedStream().toList());
        manager.setTransactionLog(properties.isTransactionLog());
        return manager;
    }

    @ConditionalOnMissingBean
    @Bean(destroyMethod = "close")
    public WriteQueueManager writeQueueManager(DataProperties properties) {
        return new WriteQueueManager(properties.getQueue());
    }

    @ConditionalOnMissingBean
    @Bean
    public RouteSession routeSession(List<EntitySession> list,
                                     EntityProxyService proxyService,
                                     LoadBalance loadbalance,IFilterManager filterManager,
                                     TransactionListenerManager listenerManager,
                                     WriteQueueManager writeQueueManager,
                                     CacheRegistry caches){
        QueryCache queryCache = caches.defaultCache();
        RouteSession build = RouteSession.builder()


                .filterManager(filterManager)

                .proxyService(proxyService)

                .clearOnWrite(queryCache.clearOnWrite())

                .transactionListenerManager(listenerManager)
                .build();

        build.setLoadBalance(loadbalance);
        build.setWrapperFactory(wrapperFactory());
        build.setQueryCache(queryCache);
        build.setWriteQueueManager(writeQueueManager);
        build.registerSession(list);
        return build;
    }

    @ConditionalOnMissingBean
    @Bean
    public SessionUtil SessionUtil(RouteSession routeSession){


        SessionUtil sessionUtil =  new SessionUtil(routeSession);


        return sessionUtil;
    }


    @ConditionalOnMissingBean
    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        return ThreadUtil.threadPoolExecutor;
    }

    @ConditionalOnMissingBean
    @Bean
    public ScheduledExecutorService scheduledExecutorService(){
        return ThreadUtil.scheduledExecutorService;
    }


    @ConditionalOnMissingBean
    @Bean

    public InitFilter initFilter(){
        return new InitFilter();
    }


}
