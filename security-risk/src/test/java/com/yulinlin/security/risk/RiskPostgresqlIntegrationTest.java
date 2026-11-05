package com.yulinlin.security.risk;

import com.yulinlin.jdbc.postgresql.PostgresqlSession;
import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.ds.PGSimpleDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a fresh schema in an explicitly supplied test PostgreSQL instance. */
@EnabledIfSystemProperty(named="pg.test.url",matches="jdbc:postgresql:.*")
class RiskPostgresqlIntegrationTest extends OrmTestSupport {
    @Test void schemaBatchCrudRecoveryAndCleanup() throws Exception {
        var source=new PGSimpleDataSource();
        source.setURL(System.getProperty("pg.test.url"));
        source.setUser(System.getProperty("pg.test.user","postgres"));
        source.setPassword(System.getProperty("pg.test.password",""));
        String schema="risk_test_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=source.getConnection();var ddl=connection.createStatement()) { ddl.execute("CREATE SCHEMA "+schema); }
        source.setCurrentSchema(schema);
        try {
            var p=RiskServiceTest.config();
            var db=new com.yulinlin.jdbc.postgresql.PostgresqlProperties();
            db.setSchemaMode(com.yulinlin.data.core.schema.SchemaMode.CREATE);
            var auto=new com.yulinlin.jdbc.postgresql.PostgresqlAutoConfiguration();
            var session=auto.postgresqlSession(source,auto.postgresqlSessionFactory(db),db);
            install(session);
            var store=new OrmBehaviorStore(p);
            store.initialize();store.initialize();
            var result=new Decision(true,Status.OBSERVING,0,List.of());
            var event=new Event("stable",RiskServiceTest.USER,"video",1000,result);
            store.writeEvents(List.of(event));store.writeEvents(List.of(event));
            assertEquals(1,store.history(RiskServiceTest.USER,0,2000,0,10).size());
            State state=new State(RiskServiceTest.USER,"stream","signature",2,0,1000,0,0,0,List.of(),1000);
            store.writeSnapshot(new Snapshot(state,List.of(new Minute(0,20,19,0,Map.of("dense",19L),2))));
            store.writeSnapshot(new Snapshot(new State(RiskServiceTest.USER,"stream","signature",1,0,0,0,0,0,List.of(),0),
                    List.of(new Minute(0,1,0,-1,Map.of(),1))));
            var restored=new ArrayList<Restored>();store.restore(0,10,restored::add);
            assertEquals(2,restored.getFirst().state().version());
            assertEquals(20,restored.getFirst().minutes().getFirst().requests());
            store.cleanup(3000,3000);
            assertTrue(store.history(RiskServiceTest.USER,0,3000,0,10).isEmpty());
        } finally {
            try(var connection=source.getConnection();var ddl=connection.createStatement()) { ddl.execute("DROP SCHEMA "+schema+" CASCADE"); }
        }
    }
}

