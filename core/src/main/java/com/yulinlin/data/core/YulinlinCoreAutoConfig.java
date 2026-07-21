package com.yulinlin.data.core;

import com.yulinlin.data.core.aop.JoinSessionAop;
import com.yulinlin.data.core.aop.JoinTransactionAop;
import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.filter.IFilter;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.filter.InitFilter;
import com.yulinlin.data.core.filter.SimpFilterManager;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.log.LogPrint;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.transaction.TransactionListener;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.core.wrapper.IWrapperFactory;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.data.lang.util.ThreadUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;


@Configuration
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
    public LogManager logManager(List<LogPrint> list) {
        return new LogManager().register(list);
    }

    @ConditionalOnMissingBean
    @Bean
    public DbCache dbCacheManager() {
        return new DbCache();
    }


    @ConditionalOnMissingBean
    @Bean
    public JoinSessionAop dataSourceAop(){
        JoinSessionAop loadBalance =   new JoinSessionAop();
        return loadBalance;
    }

    @ConditionalOnMissingBean
    @Bean
    public LoadBalance loadBalance(){
        RandomLoadBalance loadBalance =   new RandomLoadBalance();
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
    public IFilterManager filterManager(List<IFilter> filters){
        SimpFilterManager manager = new SimpFilterManager(filters);
        return manager;
    }

    @ConditionalOnMissingBean
    @Bean
    public EntityProxyService entityProxyService(){
        EntityProxyService manager =EntityProxyService.newInstance();
        return manager;
    }

    @ConditionalOnMissingBean
    @Bean
    public TransactionListenerManager transactionListenerManager(List<TransactionListener> list){
        TransactionListenerManager manager =  new TransactionListenerManager(list);
        return manager;
    }





    @ConditionalOnMissingBean
    @Bean
    public RouteSession routeSession(List<EntitySession> list,
                                     EntityProxyService proxyService,
                                     LoadBalance loadbalance,IFilterManager filterManager,TransactionListenerManager listenerManager){
        RouteSession build = RouteSession.builder()


                .filterManager(filterManager)

                .proxyService(proxyService)

                .transactionListenerManager(listenerManager)
                .build();

        build.setLoadBalance(loadbalance);
        build.setWrapperFactory(wrapperFactory());
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
