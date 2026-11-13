package com.yulinlin.security.risk;

import com.yulinlin.jdbc.h2.H2Session;
import com.yulinlin.security.risk.RiskModels.*;
import com.yulinlin.data.core.schema.SchemaMode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class OrmBehaviorStoreTest extends OrmTestSupport {
    private OrmBehaviorStore store(RiskProperties p,String mode) {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:risk_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1"+mode);
        var session=new H2Session(source);session.setCoderManager(new com.yulinlin.jdbc.h2.coder.H2CoderManager());
        install(session);var store=new OrmBehaviorStore(p);store.initialize();return store;
    }
    static State state(long version,long at,long exempt) {
        return new State(RiskServiceTest.USER,"stream","signature",version,at,exempt,List.of(),null,Map.of("sample","version-"+version));
    }
    @ParameterizedTest @ValueSource(strings={"",";MODE=MYSQL"})
    void schemaIdempotentEventsAndMonotonicCompleteSnapshots(String mode)throws Exception {
        var p=RiskServiceTest.config();p.setGroup("risk-test");var store=store(p,mode);store.initialize();
        var event=new Event("e1",RiskServiceTest.USER,"v",61_000,new Decision(true,Status.ALLOWED,0,List.of()));
        store.writeEvents(List.of(event));store.writeEvents(List.of(event));
        assertEquals(List.of(event),store.history(RiskServiceTest.USER,0,100_000,0,10));
        store.writeSnapshot(new Snapshot(state(2,61_000,0)));
        store.writeSnapshot(new Snapshot(state(3,62_000,0)));
        store.writeSnapshot(new Snapshot(state(1,60_000,0)));
        var rows=new ArrayList<Restored>();store.restore(0,10,rows::add);
        assertEquals(List.of(new Restored(state(3,62_000,0))),rows);
        store.cleanup(100_000,100_000);
        rows.clear();store.restore(0,10,rows::add);assertTrue(rows.isEmpty());
    }
    @Test void restartPreservesExactFastSamplesWithoutAddingInheritedSnapshots()throws Exception {
        var p=RiskServiceTest.small();var store=store(p,"");var time=new RiskServiceTest.Time();
        try(var first=new DefaultRiskService(p,store,List.of(),null,time)) {
            first.start();RiskServiceTest.visit(first,time,0);RiskServiceTest.visit(first,time,1000);
            first.flush().get(5,TimeUnit.SECONDS);
        }
        p.getMemory().setRestoreOnStartup(true);time.now.set(2000);
        try(var second=new DefaultRiskService(p,store,List.of(),null,time)) {
            second.start();var hit=RiskServiceTest.visit(second,time,2000);
            assertEquals(Status.SUSPECTED,hit.status());assertEquals(3,hit.components().getFirst().statistics().get("normalVideos"));
            second.flush().get(5,TimeUnit.SECONDS);
        }
        time.now.set(3000);
        try(var third=new DefaultRiskService(p,store,List.of(),null,time)) {
            third.start();assertEquals(4,RiskServiceTest.visit(third,time,3000).components().getFirst().statistics().get("normalVideos"));
        }
    }
    @Test void businessRestrictionSurvivesEvenWithoutAnyStatisticsAndAuditIsNotAuthority()throws Exception {
        var p=RiskServiceTest.small();p.setMode(RiskProperties.Mode.BLOCK);p.getMemory().setRestoreOnStartup(true);
        var store=store(p,"");var time=new RiskServiceTest.Time();var control=new RiskServiceTest.Control(time);
        control.permanent=true;
        try(var s=new DefaultRiskService(p,store,List.of(),control,time)) {
            s.start();RiskServiceTest.visit(s,time,0);RiskServiceTest.visit(s,time,1000);assertFalse(RiskServiceTest.visit(s,time,2000).allowed());
        }
        store.cleanup(Long.MAX_VALUE,Long.MAX_VALUE);
        try(var s=new DefaultRiskService(p,store,List.of(),control,time)) {
            s.start();assertFalse(RiskServiceTest.visit(s,time,3000).allowed());s.flush().get(5,TimeUnit.SECONDS);
        }
        control.release(RiskServiceTest.USER);
        try(var s=new DefaultRiskService(p,store,List.of(),control,time)) {
            s.start();assertTrue(RiskServiceTest.visit(s,time,4000).allowed());
        }
    }
    @Test void configurationChangeDiscardsOldDefaultStatistics() throws Exception {
        var p=RiskServiceTest.small();var store=store(p,"");var t=new RiskServiceTest.Time();
        try(var s=new DefaultRiskService(p,store,List.of(),null,t)) {
            s.start();RiskServiceTest.visit(s,t,0);RiskServiceTest.visit(s,t,1000);
        }
        p.getMemory().setRestoreOnStartup(true);
        p.getBehaviors().get("video").getFastSwitch().getRapid().setMinVideos(3);
        t.now.set(2000);
        try(var s=new DefaultRiskService(p,store,List.of(),null,t)) {
            s.start();assertEquals(1,RiskServiceTest.visit(s,t,2000).components().getFirst().statistics().get("normalVideos"));
        }
    }
    @Test void datasourceSchemaPolicyAndFlatGroupAreRespected() {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:risk_schema_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var session=new H2Session(source);session.setCoderManager(new com.yulinlin.jdbc.h2.coder.H2CoderManager());install(session);
        var store=new OrmBehaviorStore(RiskServiceTest.config());
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.VALIDATE);assertThrows(IllegalStateException.class,store::initialize);
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.NONE);assertThrows(Exception.class,store::initialize);
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.CREATE);store.initialize();
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.VALIDATE);store.initialize();
        var p=RiskServiceTest.config();p.setGroup("missing");assertThrows(Exception.class,()->new OrmBehaviorStore(p).initialize());
    }
    @Test void historySupportsOffsetAndFailedBatchRollsBack()throws Exception {
        var store=store(RiskServiceTest.config(),"");var decision=new Decision(true,Status.ALLOWED,0,List.of());
        var good=new Event("good",RiskServiceTest.USER,"v",0,decision);var bad=new Event("bad",RiskServiceTest.USER,"v".repeat(257),0,decision);
        assertThrows(Exception.class,()->store.writeEvents(List.of(good,bad)));assertTrue(store.history(RiskServiceTest.USER,0,100,0,10).isEmpty());
        var rows=new ArrayList<Event>();for(int i=0;i<13;i++)rows.add(new Event("e"+i,RiskServiceTest.USER,"v",i,decision));
        store.writeEvents(rows);store.writeEvents(rows);
        assertEquals(List.of(7L,8L,9L,10L,11L),store.history(RiskServiceTest.USER,0,100,7,5).stream().map(Event::occurredAt).toList());
        assertEquals(List.of(12L),store.history(RiskServiceTest.USER,0,100,12,5).stream().map(Event::occurredAt).toList());
    }
    @Test void cleanupDrainsMultipleBatchesAndRetainsUnexpiredLocalExemptions()throws Exception {
        var store=store(RiskServiceTest.config(),"");var rows=new ArrayList<Event>();
        for(int i=0;i<1200;i++)rows.add(new Event("e"+i,RiskServiceTest.USER,"v",i,new Decision(true,Status.ALLOWED,0,List.of())));
        store.writeEvents(rows);store.writeSnapshot(new Snapshot(state(1,0,300_000)));store.cleanup(2000,2000);
        assertTrue(store.history(RiskServiceTest.USER,0,2000,0,1000).isEmpty());
        var restored=new ArrayList<Restored>();store.restore(0,10,restored::add);assertEquals(1,restored.size());
        store.cleanup(2000,300_001);restored.clear();store.restore(0,10,restored::add);assertTrue(restored.isEmpty());
    }
}
