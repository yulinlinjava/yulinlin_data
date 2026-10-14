package com.yulinlin.monitor;

import com.yulinlin.data.core.YulinlinCoreAutoConfig;
import com.yulinlin.jdbc.DataJdbcApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration(after = {YulinlinCoreAutoConfig.class, DataJdbcApplication.class})
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DatabaseMonitor databaseMonitor(MonitorProperties properties) {
        return new DefaultDatabaseMonitor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MonitorDatabaseLog monitorDatabaseLog(DatabaseMonitor monitor) {
        return new MonitorDatabaseLog(monitor);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebConfiguration {
        @Bean
        @ConditionalOnMissingBean
        MonitorWebMvcConfigurer monitorWebMvcConfigurer() {
            return new MonitorWebMvcConfigurer();
        }

        @Bean
        @ConditionalOnMissingBean
        MonitorApiController monitorApiController(DatabaseMonitor monitor, MonitorProperties properties) {
            return new MonitorApiController(monitor, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        MonitorPageController monitorPageController() {
            return new MonitorPageController();
        }
    }
}
