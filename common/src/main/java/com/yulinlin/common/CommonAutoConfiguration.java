package com.yulinlin.common;

import com.yulinlin.common.model.EntityCounterManager;
import com.yulinlin.common.model.EntityCounterProperties;
import com.yulinlin.data.core.YulinlinCoreAutoConfig;
import com.yulinlin.data.core.session.RouteSession;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = YulinlinCoreAutoConfig.class)
@EnableConfigurationProperties(EntityCounterProperties.class)
public class CommonAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public EntityCounterManager entityCounterManager(RouteSession routeSession,
                                                     EntityCounterProperties properties) {
        return new EntityCounterManager(routeSession, properties);
    }
}
