package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskDetectorTest {
    private static BehaviorEvent event(String video) { return new BehaviorEvent("u","video",video); }
    @Test void rapidRequiresFiftyDistinctTargetsAndStrictlyLessThanTwoSeconds() {
        var d=new FastSwitchDetector(RiskServiceTest.config()); Detection result=null;
        for(int i=0;i<50;i++) {result=d.detect(event("v"+i),i*1000L);assertTrue(result.matches().isEmpty());}
        result=d.detect(event("v50"),50_000);
        assertEquals(List.of("rapid"),result.matches().stream().map(Match::ruleId).toList());
        assertEquals(50,result.matches().getFirst().statistics().get("fastVideos"));
        var missing=d.detect(event(null),50_001);
        assertEquals(DetectionStatus.OBSERVING,missing.status());assertTrue(missing.matches().isEmpty());
        d.reset(RiskServiceTest.USER);
        for(int i=0;i<=50;i++)result=d.detect(event("v"+i),i*2000L);
        assertTrue(result.matches().isEmpty());assertEquals(0,result.statistics().get("rapidFastVideos"));
    }
    @Test void normalAtTwoHundredVideosFourSecondsApartTriggersBeforeFifteenMinutes() {
        var d=new FastSwitchDetector(RiskServiceTest.config());
        for(int i=0;i<199;i++)assertTrue(d.detect(event("v"+i),i*4000L).matches().isEmpty());
        var result=d.detect(event("v199"),199*4000L);
        assertEquals(List.of("normal"),result.matches().stream().map(Match::ruleId).toList());
        assertEquals(200,result.statistics().get("normalVideos"));assertEquals(199,result.statistics().get("normalFastVideos"));
    }
    @Test void normalRatioBoundaryAndFiveSecondInclusiveBoundary() {
        for(int fast:List.of(139,140)) {
            var d=new FastSwitchDetector(RiskServiceTest.config());long at=0;Detection result=d.detect(event("v0"),0);
            for(int i=1;i<200;i++) {at+=i<=fast?4000:5500;result=d.detect(event("v"+i),at);}
            assertEquals(fast==140,!result.matches().isEmpty());
        }
        var p=RiskServiceTest.config();p.getBehaviors().get("video").getFastSwitch().getNormal().setMinVideos(2);
        p.getBehaviors().get("video").getFastSwitch().getNormal().setMinRatio(.5);
        var d=new FastSwitchDetector(p);d.detect(event("a"),0);
        assertEquals("normal",d.detect(event("b"),5000).matches().getFirst().ruleId());
    }
    @Test void resourceDedupDoesNotChangeAdjacentEventTimingAndNullBreaksTheEdge() {
        var d=new FastSwitchDetector(RiskServiceTest.config());
        d.detect(event("a"),0);d.detect(event("a"),10_000);
        assertEquals(1,d.detect(event("b"),11_000).statistics().get("rapidFastVideos"));
        for(int i=0;i<100;i++)assertTrue(d.detect(event(i%2==0?"a":"b"),12_000+i).matches().isEmpty());
        assertEquals(2,d.detect(event("b"),13_000).statistics().get("normalVideos"));
        d.reset(RiskServiceTest.USER);d.detect(event("a"),0);
        assertEquals(DetectionStatus.OBSERVING,d.detect(event(null),1000).status());
        assertEquals(0,d.detect(event("b"),2000).statistics().get("rapidFastVideos"));
    }
    @Test void slidingWindowRequiresBothEndpointsAndAWindowGapDoesNotResetNewSamples() {
        var d=new FastSwitchDetector(RiskServiceTest.config());
        d.detect(event("a"),0);d.detect(event("b"),1000);d.detect(event("c"),2000);
        assertEquals(1,d.detect(event(null),301_000).statistics().get("rapidFastVideos"));
        var result=d.detect(event(null),301_001);
        assertEquals(0,result.statistics().get("rapidFastVideos"));assertEquals(1,result.statistics().get("rapidVideos"));
        d.detect(event("d"),302_000);
        assertEquals(1,d.detect(event("e"),303_000).statistics().get("rapidFastVideos"));
    }
    @Test void sameResourceCanBecomeFastButNeverCountsTwiceAndFastFlagsExpire() {
        var d=new FastSwitchDetector(RiskServiceTest.config());
        d.detect(event("a"),0);d.detect(event("b"),1000);d.detect(event("a"),2000);d.detect(event("b"),3000);
        assertEquals(2,d.detect(event("b"),4000).statistics().get("rapidFastVideos"));
        var r=d.detect(event("b"),303_001);
        assertEquals(0,r.statistics().get("rapidFastVideos"));assertEquals(1,r.statistics().get("rapidVideos"));
    }
    @Test void sixHoursRandomOneThreeFiveMinuteGapsMatchButCurrentBucketIsExcluded() {
        var d=new ActiveAccessDetector(RiskServiceTest.config());long at=0;int i=0;
        while(at<21_600_000) {assertTrue(d.detect(event("v"+i),at).matches().isEmpty());at+=List.of(60_000L,180_000L,300_000L).get(i++%3);}
        var r=d.detect(event("end"),21_600_000);
        assertEquals(DetectionStatus.MATCHED,r.status());assertEquals(36,r.statistics().get("activeBuckets"));
        assertEquals(i,r.statistics().get("requests"));
    }
    @Test void coverageAndCountAreIndependentAndBoundaryIsThirtyThreeOfThirtySix() {
        for(int buckets:List.of(32,33))for(int each:List.of(1,2)) {
            var d=new ActiveAccessDetector(RiskServiceTest.config());
            for(int i=0;i<buckets;i++)for(int j=0;j<each;j++)d.detect(event("v"),i*600_000L+j);
            var r=d.detect(event("end"),21_600_000);
            assertEquals(buckets==33&&each==2,r.status()==DetectionStatus.MATCHED);
        }
    }
    @Test void hourlyBurstsCannotReplaceRestAndFirstPartialBucketNeedsFullObservation() {
        var d=new ActiveAccessDetector(RiskServiceTest.config());
        for(int hour=0;hour<6;hour++)for(int i=0;i<150;i++)d.detect(event("v"),hour*3_600_000L+i*4000);
        var r=d.detect(event("end"),21_600_000);
        assertEquals(900,r.statistics().get("requests"));assertEquals(6,r.statistics().get("activeBuckets"));
        assertEquals(DetectionStatus.NORMAL,r.status());
        d.reset(RiskServiceTest.USER);d.detect(event("first"),37_000);
        assertEquals(DetectionStatus.OBSERVING,d.detect(event("later"),21_600_000).status());
        assertEquals(DetectionStatus.NORMAL,d.detect(event("later"),22_200_000).status());
    }
    @Test void capacityIsExplicitAndExpiresInsteadOfSilentlyClaimingNormal() {
        var p=RiskServiceTest.config();p.getMemory().setMaximumEventsPerSubject(3);
        var d=new FastSwitchDetector(p);
        for(int i=0;i<3;i++)d.detect(event("same"),i);
        assertEquals(DetectionStatus.CAPACITY,d.detect(event("same"),3).status());
        assertEquals(DetectionStatus.NORMAL,d.detect(event("new"),900_004).status());
    }
    @Test void builtinSnapshotsPreserveFastEdgesAndTimeCoverage() {
        var p=RiskServiceTest.config();
        for(PersistedRiskDetector first:List.of(new FastSwitchDetector(p),new ActiveAccessDetector(p))) {
            for(int i=0;i<49;i++)first.detect(event("v"+i),i*1000L);
            PersistedRiskDetector restored=first instanceof FastSwitchDetector?new FastSwitchDetector(p):new ActiveAccessDetector(p);
            restored.restore(RiskServiceTest.USER,first.snapshot(RiskServiceTest.USER),49_000);
            for(int i=49;i<60;i++)assertEquals(first.detect(event("v"+i),i*1000L),restored.detect(event("v"+i),i*1000L));
        }
    }
    @Test void userAndBehaviorScopesAreIndependentAndSlowSamplesLeaveNormalWindow() {
        var p=RiskServiceTest.config();p.getBehaviors().put("other",new RiskProperties.Behavior());
        var d=new FastSwitchDetector(p);
        for(int i=0;i<51;i++)d.detect(event("v"+i),i*1000L);
        assertTrue(d.detect(new BehaviorEvent("other","video","v"),51_000).matches().isEmpty());
        assertTrue(d.detect(new BehaviorEvent("u","other","v"),51_000).matches().isEmpty());
        d.reset(RiskServiceTest.USER);Detection result=null;
        for(int i=0;i<200;i++)result=d.detect(event("v"+i),i*5000L);
        assertTrue(result.matches().isEmpty());assertEquals(181,result.statistics().get("normalVideos"));
    }
}
