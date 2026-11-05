package com.yulinlin.security.risk;

import com.yulinlin.security.auth.ISecurityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnProperty(prefix = "yulinlin.security.risk", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RiskProperties.class)
public class RiskAutoConfiguration {
    @Bean @ConditionalOnMissingBean(BehaviorStore.class)
    public BehaviorStore behaviorStore(RiskProperties properties) {
        return new OrmBehaviorStore(properties);
    }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean(RiskService.class)
    public DefaultRiskService riskService(RiskProperties properties, BehaviorStore store,
                                          ObjectProvider<RiskRule> rules, ObjectProvider<RiskActionHandler> handlers) {
        return new DefaultRiskService(properties, store, rules.orderedStream().toList(), handlers.orderedStream().toList());
    }
    @Bean
    public SmartInitializingSingleton riskInitializer(ObjectProvider<RiskService> services) {
        return () -> {
            RiskService service = services.getObject();
            if (service instanceof DefaultRiskService defaults) defaults.start();
        };
    }
    @Bean @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(RiskWebMvcConfigurer.class)
    public RiskWebMvcConfigurer riskWebMvcConfigurer(RiskService service, ObjectProvider<ISecurityManager> managers) {
        return new RiskWebMvcConfigurer(service, managers::getObject);
    }

    @Bean @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(RiskRequestBodyAdvice.class)
    public RiskRequestBodyAdvice riskRequestBodyAdvice(RiskWebMvcConfigurer checks, ObjectProvider<ObjectMapper> mappers) {
        return new RiskRequestBodyAdvice(checks, mappers.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()));
    }
}

