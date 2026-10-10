package com.yulinlin.data.core;

import com.yulinlin.data.core.aop.JoinSessionAop;
import com.yulinlin.data.core.aop.JoinSyncAop;
import com.yulinlin.data.core.aop.JoinTransactionAop;
import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.cache.QueryCaches;
import com.yulinlin.data.core.cache.CacheClient;
import com.yulinlin.data.core.cache.DefaultCacheClient;
import com.yulinlin.data.core.filter.IFilter;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.filter.InitFilter;
import com.yulinlin.data.core.filter.SimpFilterManager;
import com.yulinlin.data.core.http.HttpRequestClient;
import com.yulinlin.data.core.http.HttpRequestProperties;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.loadbalan.LoadBalanceProperties;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.log.LogManager;
import com.yulinlin.data.core.log.LogPrint;
import com.yulinlin.data.core.proxy.EntityProxyService;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;


@AutoConfiguration(after = {JacksonAutoConfiguration.class, RestClientAutoConfiguration.class})
@EnableConfigurationProperties({HttpRequestProperties.class, LoadBalanceProperties.class, DataProperties.class})
public class YulinlinCoreAutoConfig {

    @ConditionalOnMissingBean
    @Bean
    public HttpRequestClient httpRequestClient(
            ObjectProvider<RestClient.Builder> restClientBuilder,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            HttpRequestProperties httpRequestProperties) {
        RestClient.Builder builder = restClientBuilder.getIfAvailable(RestClient::builder);
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return HttpRequestClient.withTimeout(builder.clone(), objectMapper, httpRequestProperties.getTimeout());
    }


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
    public IFilterManager filterManager(List<IFilter> filters){
        SimpFilterManager manager = new SimpFilterManager(filters);
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
    public TransactionListenerManager transactionListenerManager(List<TransactionListener> list){
        TransactionListenerManager manager =  new TransactionListenerManager(list);
        return manager;
    }





    @ConditionalOnMissingBean
    @Bean
    public CacheClient cacheClient(ObjectProvider<QueryCache> queryCaches) {
        return new DefaultCacheClient(QueryCaches.single(queryCaches.orderedStream().toList()));
    }

    @ConditionalOnMissingBean
    @Bean
    public RouteSession routeSession(List<EntitySession> list,
                                     EntityProxyService proxyService,
                                     LoadBalance loadbalance,IFilterManager filterManager,
                                     TransactionListenerManager listenerManager,
                                     ObjectProvider<QueryCache> queryCaches){
        QueryCache queryCache = QueryCaches.single(queryCaches.orderedStream().toList());
        RouteSession build = RouteSession.builder()


                .filterManager(filterManager)

                .proxyService(proxyService)

                .transactionListenerManager(listenerManager)
                .build();

        build.setLoadBalance(loadbalance);
        build.setWrapperFactory(wrapperFactory());
        build.setQueryCache(queryCache);
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
