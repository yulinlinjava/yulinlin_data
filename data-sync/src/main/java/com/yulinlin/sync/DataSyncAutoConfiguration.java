package com.yulinlin.sync;

import com.yulinlin.data.core.YulinlinCoreAutoConfig;
import com.yulinlin.data.core.session.RouteSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = YulinlinCoreAutoConfig.class)
@EnableConfigurationProperties(DataSyncProperties.class)
@ConditionalOnProperty(prefix = "yulinlin.sync", name = "enabled", havingValue = "true")
public class DataSyncAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    public SyncTaskStore syncTaskStore() { return new MemorySyncTaskStore(); }

    @Bean @ConditionalOnMissingBean
    public DataSyncClient dataSyncClient(ObjectProvider<RouteSession> routes, SyncManager manager,
                                         DataSyncProperties properties) {
        return new DataSyncClient(routes::getObject, manager, properties);
    }

    @Bean @ConditionalOnMissingBean
    public SyncWriter syncWriter(ObjectProvider<RouteSession> routes) {
        // 延迟解析，避免 RouteSession -> FilterManager -> SyncManager -> RouteSession 循环依赖。
        return task -> {
            var request = task.newRequest();
            // 每个 JDBC 重放请求保持单连接原子性，不继承源请求的独立批量提示。
            request.setBatch(false);
            Integer affected = routes.getObject().replayWrite(task.rule().target(), request);
            if (affected == null)
                throw new IllegalStateException("目标同步未返回执行结果，规则：" + task.rule().name());
        };
    }

    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    public SyncManager syncManager(DataSyncProperties properties, SyncTaskStore store, SyncWriter writer) {
        return new SyncManager(properties, store, writer);
    }

    @Bean @ConditionalOnMissingBean
    public SyncRequestFilter syncRequestFilter(SyncManager manager) { return new SyncRequestFilter(manager); }

    @Bean
    public SmartInitializingSingleton dataSyncInitializer(SyncManager manager, ObjectProvider<RouteSession> routes) {
        return () -> manager.start(routes.getObject().loadBalanceList());
    }
}
