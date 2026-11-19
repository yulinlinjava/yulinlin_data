package com.yulinlin.monitor;

import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.proxy.EntityProxyService;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.mysql.MysqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real local JDBC execution with a MySQL Session; no external server is contacted. */
class SqlRiskIntegrationTest {
    @Test void cachedSafeSqlStillBlocksAppendedSqlWithoutAffectingLaterSafeExecution() {
        var fixture = new Fixture(new DruidSqlRiskDetector());
        String single = "SELECT '; SELECT 2' AS text; /* trailing ; */";
        assertEquals(1, fixture.session.select(query(single, Map.of())).size());
        assertEquals(1, fixture.opens.get());
        var error = assertThrows(SqlRiskException.class,
                () -> fixture.session.select(query(single + " SELECT 3", Map.of())));
        assertFalse(error.isInspectionFailed());
        assertEquals(1, fixture.opens.get(), "追加语句不得获取 JDBC 连接");
        assertEquals(1, fixture.session.select(query(single, Map.of())).size());
        assertEquals(2, fixture.opens.get());
        assertEquals(1, fixture.risks.recent(10).size());
        assertTrue(fixture.risks.recent(10).getFirst().blocked());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1 AS id;",
            "SELECT 1 AS id; \r\n\t",
            "SELECT '; SELECT 2' AS text",
            "SELECT 'it''s; SELECT 2' AS text;",
            "SELECT /* ; SELECT 2 */ 1 AS id",
            "SELECT 1 AS id -- ; SELECT 2\n",
            "SELECT 1 AS id; /* trailing ; SELECT 2 */",
            "SELECT 1 AS id; -- trailing ; SELECT 2\n"
    })
    void harmlessSemicolonsExecuteNormallyInDefaultBlockMode(String sql) {
        var fixture = new Fixture(new DruidSqlRiskDetector());
        for (int i = 0; i < 2; i++) {
            assertEquals(1, fixture.session.select(query(sql, Map.of())).size());
        }
        assertEquals(2, fixture.opens.get());
        assertTrue(fixture.risks.recent(10).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1; SELECT 2",
            "SELECT 1;\nSELECT 2;",
            "SELECT 1; /* comment ; */ SELECT 2",
            "SELECT 1; -- comment ;\n SELECT 2",
            "SELECT '; SELECT 2' AS text; SELECT 3"
    })
    void appendedStatementsAreBlockedBeforeJdbcEvenOnRepeatedChecks(String sql) {
        var fixture = new Fixture(new DruidSqlRiskDetector());
        for (int i = 0; i < 2; i++) {
            var error = assertThrows(SqlRiskException.class,
                    () -> fixture.session.select(query(sql, Map.of())));
            assertFalse(error.isInspectionFailed(), "应识别为多语句违规，而不是解析失败");
        }
        assertEquals(0, fixture.opens.get());
        var records = fixture.risks.recent(10);
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(SqlRiskRecord::blocked));
    }

    @Test void blocksBeforeOpeningAConnectionAndDoesNotReportPhysicalExecution() {
        var fixture = new Fixture(new DruidSqlRiskDetector());
        var completions = new AtomicInteger();
        fixture.session.setExecutionListeners(List.of(event -> completions.incrementAndGet()));
        var error = assertThrows(SqlRiskException.class,
                () -> fixture.session.select(query("SELECT SLEEP(?)", Map.of())));
        assertFalse(error.isInspectionFailed());
        assertEquals(0, fixture.opens.get());
        assertEquals(0, completions.get());
        assertTrue(fixture.risks.recent(10).getFirst().blocked());
    }

    @Test void observeExecutesAndOffDoesNotCallDetector() {
        var calls = new AtomicInteger();
        var fixture = new Fixture(sql -> { calls.incrementAndGet(); return SqlRiskResult.reject("业务规则"); });
        fixture.properties.getSqlRisk().setMode(MonitorProperties.SqlRiskMode.OBSERVE);
        assertEquals(1, fixture.session.select(query("SELECT 1 AS id", Map.of())).size());
        assertEquals(1, fixture.opens.get());
        assertFalse(fixture.risks.recent(10).getFirst().blocked());
        fixture.properties.getSqlRisk().setMode(MonitorProperties.SqlRiskMode.OFF);
        fixture.session.select(query("SELECT 2 AS id", Map.of()));
        assertEquals(1, calls.get());
        assertEquals(1, fixture.risks.recent(10).size());
    }

    @Test void detectorOnlySeesFinalTemplateAndDoesNotSeeBoundValue() {
        var seen = new ArrayList<String>();
        var druid = new DruidSqlRiskDetector();
        var fixture = new Fixture(sql -> { seen.add(sql); return druid.check(sql); });
        String value = "'; DROP TABLE example; --";
        var rows = fixture.session.select(query("SELECT #{value} AS text", Map.of("value", value)));
        assertEquals(1, rows.size());
        assertTrue(seen.getFirst().contains("?"));
        assertFalse(seen.getFirst().contains(value));
        assertTrue(fixture.risks.recent(10).isEmpty());
        assertThrows(SqlRiskException.class, () -> fixture.session.select(
                query("SELECT ${expression}", Map.of("expression", "SLEEP(1)"))));
        assertTrue(seen.getLast().contains("SLEEP(1)"));
    }

    @Test void batchChecksAllTemplatesBeforeAnyWritesOrWorkerSubmissions() {
        var fixture = new Fixture(new DruidSqlRiskDetector());
        var request = ExecuteRequest.newInstance("INSERT INTO example (id) VALUES (#{id})", Map.of("id", 1));
        request.addRequest(new CommandNode("DELETE FROM example; DROP TABLE example", Map.of(), ParseType.update));
        request.setBatch(true);
        fixture.session.setParallelConnections(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            fixture.session.setThreadPoolExecutor((java.util.concurrent.ThreadPoolExecutor) executor);
            assertThrows(SqlRiskException.class, () -> fixture.session.execute(request, RequestType.update));
        }
        assertEquals(0, fixture.opens.get());
    }

    @Test void batchChecksRepeatedTemplateOnlyOnce() {
        var calls = new AtomicInteger();
        var fixture = new Fixture(sql -> { calls.incrementAndGet(); return SqlRiskResult.allow(); });
        fixture.session.update(ExecuteRequest.newInstance("CREATE TABLE example (id INT)", Map.of()));
        calls.set(0);
        var request = ExecuteRequest.newInstance("INSERT INTO example (id) VALUES (1)", Map.of());
        request.addRequest(new CommandNode("INSERT INTO example (id) VALUES (1)", Map.of(), ParseType.update));
        assertEquals(2, fixture.session.update(request));
        assertEquals(1, calls.get());
    }

    @Test void selectEachStillUsesTheGuard() {
        var fixture = new Fixture(sql -> SqlRiskResult.reject("拒绝"));
        assertThrows(SqlRiskException.class, () -> fixture.session.selectEach(query("SELECT 1", Map.of()), row -> fail()));
        assertEquals(0, fixture.opens.get());
    }

    @ParameterizedTest
    @EnumSource(value = MonitorProperties.SqlRiskMode.class, names = {"BLOCK", "OBSERVE"})
    void frameworkSchemaCreatesTablesIndexesAndColumnsWithoutCallingDetector(MonitorProperties.SqlRiskMode mode)
            throws Exception {
        var calls = new AtomicInteger();
        var fixture = new Fixture(sql -> { calls.incrementAndGet(); return SqlRiskResult.reject("拒绝所有业务 SQL"); });
        fixture.properties.getSqlRisk().setMode(mode);
        try (var connection = fixture.source.getConnection(); var statement = connection.createStatement()) {
            fixture.session.schema(statement, "CREATE TABLE example (id INT PRIMARY KEY)");
            fixture.session.schema(statement, "ALTER TABLE example ADD COLUMN name VARCHAR(50)");
            fixture.session.schema(statement, "CREATE INDEX idx_example_name ON example (name)");
            try (var columns = connection.getMetaData().getColumns(null, null, "EXAMPLE", "NAME")) {
                assertTrue(columns.next(), "框架补字段应成功");
            }
            try (var indexes = connection.getMetaData().getIndexInfo(null, null, "EXAMPLE", false, false)) {
                boolean found = false;
                while (indexes.next()) found |= "IDX_EXAMPLE_NAME".equals(indexes.getString("INDEX_NAME"));
                assertTrue(found, "框架建索引应成功");
            }
        }
        assertEquals(0, calls.get());
        assertTrue(fixture.risks.recent(10).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE TABLE example (id INT PRIMARY KEY)",
            "ALTER TABLE example ADD COLUMN name VARCHAR(50)",
            "CREATE INDEX idx_example_name ON example (name)",
            "DROP TABLE example"
    })
    void businessDdlStillCallsDetectorAndCanBeBlocked(String sql) {
        var seen = new ArrayList<String>();
        var fixture = new Fixture(template -> { seen.add(template); return SqlRiskResult.reject("业务规则"); });
        assertThrows(SqlRiskException.class, () -> fixture.session.update(ExecuteRequest.newInstance(sql, Map.of())));
        assertEquals(List.of(sql), seen);
        assertEquals(0, fixture.opens.get());
        assertTrue(fixture.risks.recent(1).getFirst().blocked());
    }

    @Test void businessCreateFollowedByDropIsStillRejectedByDruid() {
        var fixture = new Fixture(new DruidSqlRiskDetector());
        assertThrows(SqlRiskException.class, () -> fixture.session.update(ExecuteRequest.newInstance(
                "CREATE TABLE example (id INT); DROP TABLE example", Map.of())));
        assertEquals(0, fixture.opens.get());
    }

    @Test void listCountPageGroupAndExplainAllCheckTheirFinalTemplates() {
        var seen = new ArrayList<String>();
        var fixture = new Fixture(sql -> { seen.add(sql); return SqlRiskResult.reject("reject"); });
        var select = new com.yulinlin.data.core.wrapper.impl.SelectWrapper<Map>();
        select.table("example");
        select.fields().field("id", "id");
        select.where().eq("id", 1);
        var request = QueryRequest.newInstance(Map.class, select);
        assertThrows(SqlRiskException.class, () -> fixture.session.select(request));
        assertThrows(SqlRiskException.class, () -> fixture.session.count(request));
        assertThrows(SqlRiskException.class, () -> fixture.session.page(request));
        assertThrows(SqlRiskException.class, () -> fixture.session.explain(request));
        assertThrows(SqlRiskException.class, () -> fixture.session.explainCount(request));
        var group = new com.yulinlin.data.core.wrapper.impl.GroupWrapper<Map>();
        group.table("example");
        group.aggregations().field("id", "id");
        group.metrics().count("id", "total");
        assertThrows(SqlRiskException.class, () -> fixture.session.group(QueryRequest.newInstance(Map.class, group)));
        assertEquals(6, seen.size());
        assertTrue(seen.get(1).toLowerCase().contains("count("));
        assertTrue(seen.get(3).startsWith("EXPLAIN "));
        assertTrue(seen.get(4).toLowerCase().contains("count("));
        assertTrue(seen.get(5).toLowerCase().contains("group by"));
        assertEquals(0, fixture.opens.get());
    }

    @Test void escapingRiskExceptionRollsBackOuterTransactionButCaughtRejectionDoesNotPoisonIt() throws Exception {
        var fixture = new Fixture(new DruidSqlRiskDetector());
        fixture.session.update(ExecuteRequest.newInstance("CREATE TABLE example (id INT)", Map.of()));
        var route = com.yulinlin.data.core.session.RouteSession.builder()
                .proxyService(EntityProxyService.newInstance()).build();
        route.setLoadBalance(new com.yulinlin.data.core.loadbalan.RandomLoadBalance());
        route.registerSession(fixture.session);
        assertThrows(SqlRiskException.class, () -> route.transaction(() -> {
            route.update(ExecuteRequest.newInstance("INSERT INTO example VALUES (1)", Map.of()));
            route.select(query("SELECT SLEEP(1)", Map.of()));
            return null;
        }));
        assertEquals(0, countRows(fixture));
        route.transaction(() -> {
            route.update(ExecuteRequest.newInstance("INSERT INTO example VALUES (2)", Map.of()));
            assertThrows(SqlRiskException.class, () -> route.select(query("SELECT SLEEP(1)", Map.of())));
            return null;
        });
        assertEquals(1, countRows(fixture));
    }

    private int countRows(Fixture fixture) throws SQLException {
        try (var connection = fixture.source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM example")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test void inspectionFailuresFailClosedInBlockAndContinueInObserve() {
        var fixture = new Fixture(sql -> { throw new IllegalStateException("detector unavailable"); });
        var error = assertThrows(SqlRiskException.class, () -> fixture.session.select(query("SELECT 1", Map.of())));
        assertTrue(error.isInspectionFailed());
        assertEquals(0, fixture.opens.get());
        fixture.properties.getSqlRisk().setMode(MonitorProperties.SqlRiskMode.OBSERVE);
        assertEquals(1, fixture.session.select(query("SELECT 1", Map.of())).size());
        assertTrue(fixture.risks.recent(1).getFirst().inspectionFailed());
    }

    @Test void riskHistoryIsBoundedAndDetectionSeesUntruncatedSql() {
        var seen = new ArrayList<String>();
        var fixture = new Fixture(sql -> { seen.add(sql); return SqlRiskResult.reject("reason"); });
        fixture.properties.setMaxRecords(2);
        var guard = new SqlRiskExecutionGuard(sql -> { seen.add(sql); return SqlRiskResult.reject("reason"); },
                fixture.properties, fixture.risks);
        String longSql = "SELECT 1 /*" + "x".repeat(5000) + "*/; SELECT 2";
        for (int i = 0; i < 3; i++) assertThrows(SqlRiskException.class, () -> guard.check(fixture.session, longSql));
        assertEquals(longSql, seen.getFirst());
        assertEquals(2, fixture.risks.recent(99).size());
        assertEquals(4096, fixture.risks.recent(1).getFirst().sql().length());
    }

    static QueryRequest<Map> query(String sql, Map<String, Object> values) {
        return QueryRequest.newInstance(sql, values, Map.class);
    }

    static class TestSession extends MysqlSession {
        TestSession(javax.sql.DataSource source) { super(source); }
        int schema(java.sql.Statement statement, String sql) throws SQLException { return executeSchemaSql(statement, sql); }
    }

    static class Fixture {
        final MonitorProperties properties = new MonitorProperties();
        final SqlRiskMonitor risks = new SqlRiskMonitor(properties);
        final AtomicInteger opens = new AtomicInteger();
        final JdbcDataSource source = new JdbcDataSource();
        final TestSession session;

        Fixture(SqlRiskDetector detector) {
            source.setURL("jdbc:h2:mem:risk_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            session = new TestSession(new DelegatingDataSource(source) {
                @Override public Connection getConnection() throws SQLException {
                    opens.incrementAndGet();
                    return super.getConnection();
                }
            });
            session.setGroup("test-mysql");
            session.setCoderManager(new JdbcCoderManager());
            session.setProxyService(EntityProxyService.newInstance());
            session.setSqlGuards(List.of(new SqlRiskExecutionGuard(detector, properties, risks)));
        }
    }
}
