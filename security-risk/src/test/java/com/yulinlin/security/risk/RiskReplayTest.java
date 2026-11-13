package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RiskReplayTest {
    private static ReplayEvent e(long at) {return new ReplayEvent("u","video","v"+at,at);}
    @Test void stableOrderingSourceIndexesAndFreshStateEachRun() {
        var input=new ArrayList<>(List.of(e(2000),e(0),e(1000),e(1000)));
        var original=List.copyOf(input);var replay=new RiskReplay(RiskServiceTest.small());
        var result=replay.replay(input);
        assertEquals(original,input);assertEquals(List.of(1,2,3,0),result.stream().map(ReplayResult::inputIndex).toList());
        assertEquals(Status.SUSPECTED,result.getLast().decision().status());assertEquals(result,replay.replay(input));
        assertThrows(UnsupportedOperationException.class,result::clear);
    }
    @Test void replayNeverCreatesRestrictionEvenWhenConfiguredBlockAndCustomFactoriesAreIsolated() {
        var p=RiskServiceTest.config();p.setMode(RiskProperties.Mode.BLOCK);
        p.getBehaviors().get("video").setComponents(List.of("custom"));
        var count=new AtomicInteger();var instances=new ArrayList<RiskServiceTest.CountingDetector>();
        var replay=new RiskReplay(p,()->{count.incrementAndGet();var d=new RiskServiceTest.CountingDetector("custom",true);instances.add(d);return List.of(d);});
        var a=replay.replay(List.of(e(0)));var b=replay.replay(List.of(e(0)));
        assertEquals(a,b);assertEquals(2,count.get());assertNotSame(instances.get(0),instances.get(1));
        assertTrue(a.getFirst().decision().allowed());assertNull(a.getFirst().decision().restriction());
        assertEquals(Status.SUSPECTED,a.getFirst().decision().status());assertEquals(1,instances.getFirst().resets.get());
    }
    @Test void historicalTimeDrivesDedupExpiryAndSubjectsStayIsolated() {
        var p=RiskServiceTest.small();
        var rows=List.of(new ReplayEvent("u","video","a","id",0),new ReplayEvent("u","video","b","id",1000),
                new ReplayEvent("other","video","c","id",2000),
                new ReplayEvent("u","video","d","id",86_400_000L),new ReplayEvent("u","video","e",86_401_000L));
        var result=new RiskReplay(p).replay(rows);
        assertEquals(1,result.get(1).decision().components().getFirst().statistics().get("normalVideos"));
        assertEquals(1,result.get(3).decision().components().getFirst().statistics().get("normalVideos"));
        assertEquals(2,result.getLast().decision().components().getFirst().statistics().get("normalVideos"));
    }
    @Test void liveAndReplayShareEveryDetectorResult() {
        var p=RiskServiceTest.config();var time=new RiskServiceTest.Time();
        var rows=new ArrayList<ReplayEvent>();
        for(int i=0;i<=300;i++)rows.add(e(i*4000L));
        var expected=new ArrayList<List<Detection>>();
        try(var s=RiskServiceTest.service(p,new RiskServiceTest.Store(),time)) {
            for(var row:rows){time.now.set(row.occurredAt());expected.add(s.checkAndRecord(row.event()).components());}
        }
        assertEquals(expected,new RiskReplay(p).replay(rows).stream().map(r->r.decision().components()).toList());
    }
    @Test void noSyntheticFinalRequestAndInvalidInputsAreRejected() {
        var replay=new RiskReplay(RiskServiceTest.small());
        assertTrue(replay.replay(List.of(e(0),e(1000))).getLast().decision().matches().isEmpty());
        assertEquals(List.of(),replay.replay(List.of()));
        assertThrows(NullPointerException.class,()->replay.replay(null));
        assertThrows(IllegalArgumentException.class,()->new ReplayEvent("u","video","v",-1));
        assertThrows(IllegalArgumentException.class,()->replay.replay(List.of(new ReplayEvent("u","bad","v",0))));
    }
}
