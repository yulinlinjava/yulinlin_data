package com.yulinlin.security.risk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class RiskConfigurationTest {
    @Test void disabledModuleDoesNotRequireDatasourceOrCreateService() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RiskAutoConfiguration.class))
                .run(context -> assertEquals(0,context.getBeansOfType(RiskService.class).size()));
    }
    @Test void bindsYamlEquivalentPropertiesAndCustomStoreWithoutJdbc() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RiskAutoConfiguration.class))
                .withBean(BehaviorStore.class,RiskServiceTest.Store::new)
                .withPropertyValues("yulinlin.security.risk.enabled=true",
                        "yulinlin.security.risk.memory.restore-on-startup=false",
                        "yulinlin.security.risk.behaviors.video.rules[0].id=continuous",
                        "yulinlin.security.risk.behaviors.video.rules[0].type=ACTIVE_MINUTES",
                        "yulinlin.security.risk.behaviors.video.rules[0].window=120m",
                        "yulinlin.security.risk.behaviors.video.rules[0].min-active-minutes=120")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(RiskService.class));
                    assertEquals(Duration.ofHours(2),context.getBean(RiskProperties.class).getBehaviors().get("video").getRules().getFirst().getWindow());
                });
    }
    @Test void invalidCapacityWindowRatioAndUnknownRuleFailEarly() {
        var p=RiskServiceTest.config();p.getAsync().setQueueCapacity(1);assertThrows(IllegalArgumentException.class,p::validate);
        p=RiskServiceTest.config();p.getBehaviors().get("video").getRules().getFirst().setWindow(Duration.ofSeconds(90));
        assertThrows(IllegalArgumentException.class,p::validate);
        p=RiskServiceTest.config();p.getBehaviors().get("video").getRules().getFirst().setMinShortGapRatio(Double.NaN);
        assertThrows(IllegalArgumentException.class,p::validate);
        p=RiskServiceTest.config();p.getBehaviors().get("video").getRules().getFirst().setType("typo");
        var invalid=p;assertThrows(IllegalArgumentException.class,()->new DefaultRiskService(invalid,new RiskServiceTest.Store(),java.util.List.of(),java.util.List.of()));
    }
}

