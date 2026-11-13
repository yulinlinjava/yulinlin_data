package com.yulinlin.security.risk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiskConfigurationTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RiskAutoConfiguration.class))
                .withBean(BehaviorStore.class,RiskServiceTest.Store::new);
    }
    private ApplicationContextRunner enabled() {
        return context().withPropertyValues("yulinlin.security.risk.enabled=true",
                "yulinlin.security.risk.memory.restore-on-startup=false",
                "yulinlin.security.risk.behaviors.video.components[0]=fast-switch");
    }
    @Test void disabledNeedsNoDatasourceOrControl() {
        context().run(c->assertEquals(0,c.getBeansOfType(RiskService.class).size()));
    }
    @Test void flatGroupAndDefaultThresholdsBind() {
        enabled().withPropertyValues("yulinlin.security.risk.group=risk-db").run(c->{
            assertNull(c.getStartupFailure());var p=c.getBean(RiskProperties.class);
            assertEquals("risk-db",p.getGroup());var b=p.getBehaviors().get("video");
            assertEquals(Duration.ofMinutes(5),b.getFastSwitch().getRapid().getWindow());
            assertEquals(50,b.getFastSwitch().getRapid().getMinVideos());
            assertEquals(Duration.ofMinutes(15),b.getFastSwitch().getNormal().getWindow());
            assertEquals(200,b.getFastSwitch().getNormal().getMinVideos());
            assertEquals(Duration.ofHours(6),b.getActiveAccess().getWindow());
        });
    }
    @Test void blockRequiresBusinessControlButAnObserveOverrideDoesNot() {
        enabled().withPropertyValues("yulinlin.security.risk.mode=BLOCK").run(c->assertNotNull(c.getStartupFailure()));
        enabled().withPropertyValues("yulinlin.security.risk.mode=BLOCK",
                "yulinlin.security.risk.behaviors.video.fast-switch.mode=OBSERVE").run(c->assertNull(c.getStartupFailure()));
        enabled().withPropertyValues("yulinlin.security.risk.mode=BLOCK")
                .withBean(RiskControl.class,()->new RiskServiceTest.Control(new RiskServiceTest.Time()))
                .run(c->assertNull(c.getStartupFailure()));
    }
    @Test void unknownDuplicateComponentsAndOldConfigurationFailAtStartup() {
        enabled().withPropertyValues("yulinlin.security.risk.behaviors.video.components[0]=typo").run(c->assertNotNull(c.getStartupFailure()));
        enabled().withPropertyValues("yulinlin.security.risk.behaviors.video.components[1]=fast-switch").run(c->assertNotNull(c.getStartupFailure()));
        enabled().withBean("conflict",RiskDetector.class,()->new RiskServiceTest.CountingDetector("fast-switch",false))
                .run(c->assertNotNull(c.getStartupFailure()));
        for(String old:List.of("block-duration=30m","storage.group=old","behaviors.video.rules[0].type=ACTIVE_MINUTES"))
            enabled().withPropertyValues("yulinlin.security.risk."+old).run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void customComponentIsDiscoveredAndSelectedWithoutReplacingBuiltins() {
        enabled().withBean(RiskDetector.class,()->new RiskServiceTest.CountingDetector("custom",true))
                .withPropertyValues("yulinlin.security.risk.behaviors.video.components[1]=custom")
                .run(c->{assertNull(c.getStartupFailure());
                    var r=c.getBean(RiskService.class).checkAndRecord(new com.yulinlin.security.risk.RiskModels.BehaviorEvent("u","video","v"));
                    assertEquals(2,r.components().size());assertEquals("custom",r.matches().getFirst().componentId());
                });
    }
    @Test void invalidCapacitiesRatiosAndWindowsAreRejected() {
        var p=RiskServiceTest.config();p.getAsync().setQueueCapacity(1);assertThrows(IllegalArgumentException.class,p::validate);
        p=RiskServiceTest.config();p.getBehaviors().get("video").getActiveAccess().setWindow(Duration.ofMinutes(61));assertThrows(IllegalArgumentException.class,p::validate);
        p=RiskServiceTest.config();p.getBehaviors().get("video").getFastSwitch().getNormal().setMinRatio(Double.NaN);assertThrows(IllegalArgumentException.class,p::validate);
        p=RiskServiceTest.config();p.getMemory().setMaximumEventsPerSubject(49);assertThrows(IllegalArgumentException.class,p::validate);
    }
}
