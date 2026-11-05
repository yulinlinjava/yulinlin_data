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
    @Test void cleanupDrainsMoreThanOneBatch() throws Exception {
        var store=store(RiskServiceTest.config(),"");
        var rows=new ArrayList<Event>();
        for(int i=0;i<1200;i++) rows.add(new Event("e"+i,RiskServiceTest.USER,"v",i,new Decision(true,Status.ALLOWED,0,List.of())));
        store.writeEvents(rows);store.cleanup(2000,2000);
        assertTrue(store.history(RiskServiceTest.USER,0,2000,0,1000).isEmpty());
    }
    private OrmBehaviorStore store(RiskProperties properties, String mode) throws Exception {
        var source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:risk_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1"+mode);
        var session=new H2Session(source);
        session.setCoderManager(new com.yulinlin.jdbc.h2.coder.H2CoderManager());
        install(session);
        var store=new OrmBehaviorStore(properties); store.initialize(); return store;
    }
    @ParameterizedTest @ValueSource(strings={"",";MODE=MYSQL"})
    void createsTablesAndRetriesAreIdempotentAndOldSnapshotsCannotOverwrite(String mode) throws Exception {
        var p=RiskServiceTest.config(); var store=store(p,mode);
        store.initialize();
        var decision=new Decision(true,Status.ALLOWED,0,List.of());
        var event=new Event("event-1",RiskServiceTest.USER,"v",61_000,decision);
        store.writeEvents(List.of(event)); store.writeEvents(List.of(event));
        assertEquals(1,store.history(RiskServiceTest.USER,0,100_000,0,10).size());
        var state=new State(RiskServiceTest.USER,"stream","sig",2,0,61_000,0,0,0,List.of(),61_000);
        var minute=new Minute(60_000,20,19,60_000,Map.of("dense",19L),2);
        store.writeSnapshot(new Snapshot(state,List.of(minute)));
        var updated=new State(RiskServiceTest.USER,"stream","sig",3,0,62_000,0,0,0,List.of(),62_000);
        store.writeSnapshot(new Snapshot(updated,List.of(new Minute(60_000,21,20,60_000,Map.of("dense",20L),3))));
        var old=new State(RiskServiceTest.USER,"stream","sig",1,0,60_000,0,0,0,List.of(),60_000);
        store.writeSnapshot(new Snapshot(old,List.of(new Minute(60_000,1,0,-1,Map.of(),1))));
        var restored=new ArrayList<Restored>();
        store.restore(0,10,restored::add);
        assertEquals(1,restored.size()); assertEquals(3,restored.getFirst().state().version());
        assertEquals(21,restored.getFirst().minutes().getFirst().requests());
        store.cleanup(100_000,100_000);
        assertTrue(store.history(RiskServiceTest.USER,0,100_000,0,10).isEmpty());
    }
    @Test void restartRestoresLatestSnapshotWithoutSummingInheritedSnapshots() throws Exception {
        var p=RiskServiceTest.config(); var store=store(p,"");
        var time=new RiskServiceTest.Time();
        try(var first=new DefaultRiskService(p,store,List.of(),List.of(),time)) {
            first.start();
            RiskServiceTest.visit(first,time,0); RiskServiceTest.visit(first,time,60_000);
            first.flush().get(5,TimeUnit.SECONDS);
        }
        p.getMemory().setRestoreOnStartup(true);
        time.now.set(120_000);
        try(var second=new DefaultRiskService(p,store,List.of(),List.of(),time)) {
            second.start(); second.flush().get(5,TimeUnit.SECONDS);
            assertEquals(2,second.inspect(RiskServiceTest.USER).orElseThrow().minutes().stream().mapToLong(Minute::requests).sum());
            assertFalse(RiskServiceTest.visit(second,time,120_000).allowed());
            second.flush().get(5,TimeUnit.SECONDS);
        }
        time.now.set(121_000);
        try(var third=new DefaultRiskService(p,store,List.of(),List.of(),time)) {
            third.start(); third.flush().get(5,TimeUnit.SECONDS);
            assertEquals(3,third.inspect(RiskServiceTest.USER).orElseThrow().minutes().stream().mapToLong(Minute::requests).sum());
            assertFalse(RiskServiceTest.visit(third,time,121_000).allowed());
        }
    }
    @Test void datasourceSchemaPolicyIsRespected() throws Exception {
        var source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:risk_schema_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var session=new H2Session(source);
        session.setCoderManager(new com.yulinlin.jdbc.h2.coder.H2CoderManager());
        install(session);
        var store=new OrmBehaviorStore(RiskServiceTest.config());
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.VALIDATE);
        assertThrows(IllegalStateException.class,store::initialize);
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.NONE);
        assertThrows(Exception.class,store::initialize);
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.CREATE);
        store.initialize();
        org.springframework.test.util.ReflectionTestUtils.setField(session,"schemaMode",SchemaMode.VALIDATE);
        store.initialize();
    }
    @Test void historySupportsOffsetAndFailedBatchRollsBack() throws Exception {
        var store=store(RiskServiceTest.config(),"");
        var decision=new Decision(true,Status.ALLOWED,0,List.of());
        var good=new Event("good",RiskServiceTest.USER,"v",0,decision);
        var bad=new Event("bad",RiskServiceTest.USER,"v".repeat(257),0,decision);
        assertThrows(Exception.class,()->store.writeEvents(List.of(good,bad)));
        assertTrue(store.history(RiskServiceTest.USER,0,100,0,10).isEmpty());
        var rows=new ArrayList<Event>();
        for(int i=0;i<13;i++) rows.add(new Event("e"+i,RiskServiceTest.USER,"v",i,decision));
        store.writeEvents(rows);store.writeEvents(rows);
        assertEquals(List.of(7L,8L,9L,10L,11L),store.history(RiskServiceTest.USER,0,100,7,5).stream().map(Event::occurredAt).toList());
        assertEquals(List.of(12L),store.history(RiskServiceTest.USER,0,100,12,5).stream().map(Event::occurredAt).toList());
    }
    @Test void cleanupDoesNotRemoveUnexpiredRestrictions() throws Exception {
        var p=RiskServiceTest.config(); var store=store(p,"");
        var state=new State(RiskServiceTest.USER,"stream","sig",1,0,0,300_000,0,0,List.of(),0);
        store.writeSnapshot(new Snapshot(state,List.of()));
        store.cleanup(100_000,200_000);
        var restored=new ArrayList<Restored>(); store.restore(0,10,restored::add);
        assertEquals(1,restored.size());
        store.cleanup(100_000,300_001);
        restored.clear();store.restore(0,10,restored::add);assertTrue(restored.isEmpty());
    }
    @Test void longRestrictionRestoresEvenWhenObservationIsOlderThanRetention() throws Exception {
        var p=RiskServiceTest.config(); var store=store(p,"");
        long cutoff=1_000_000, until=cutoff+p.getMemory().getRetention().toMillis()+60_000;
        var state=new State(RiskServiceTest.USER,"stream","sig",1,0,0,until,0,0,List.of(),0);
        store.writeSnapshot(new Snapshot(state,List.of()));
        var restored=new ArrayList<Restored>();store.restore(cutoff,10,restored::add);
        assertEquals(until,restored.getFirst().state().blockedUntil());
    }
}

