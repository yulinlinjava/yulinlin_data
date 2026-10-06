package com.yulinlin.jdbc.postgresql;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.base.Eq;
import com.yulinlin.data.core.node.base.Match;
import com.yulinlin.data.core.node.select.AsField;
import com.yulinlin.data.core.node.group.DateGroup;
import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.SqlNode;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.sql.SqlParseManager;
import com.yulinlin.jdbc.sql.parse.SqlJsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresqlSessionTest {
    private final PostgresqlSession pg = new PostgresqlSession(null);
    private SimpParamsContext params(RequestType type) {
        return new SimpParamsContext(type, Map.of(), new JdbcCoderManager().createEncoderBuffer(), Map.class, false);
    }
    private SqlNode node(JdbcSession session, INode wrapper, RequestType type) {
        return (SqlNode) ((ParseResult) session.parseSql(wrapper, params(type))).getRequest();
    }
    private String sql(SqlNode node) { return node.getSql().replaceAll("\\s+", " ").trim(); }

    @Test void bindsModuleOwnedCommonSettings() {
        var source = new MapConfigurationPropertySource(Map.of(
                "yulinlin.postgresql.log", true,
                "yulinlin.postgresql.map-underscore-to-camel-case", false,
                "yulinlin.postgresql.parallel-connections", 2,
                "yulinlin.postgresql.execute-batch-size", 64));
        var properties = new Binder(source).bind(
                "yulinlin.postgresql", Bindable.of(PostgresqlProperties.class))
                .orElseThrow(() -> new AssertionError("PostgreSQL properties were not bound"));
        assertThat(properties.isLog()).isTrue();
        assertThat(properties.isMapUnderscoreToCamelCase()).isFalse();
        assertThat(properties.getParallelConnections()).isEqualTo(2);
        assertThat(properties.getExecuteBatchSize()).isEqualTo(64);
    }

    @Test void registersSharedParsersDirectlyWithoutCompatibilitySubclasses() {
        var shared = new SqlParseManager();
        var actual = (SqlParseManager) pg.getParseManager();
        assertThat(actual).isExactlyInstanceOf(PostgresqlParseManager.class);
        assertThat(shared.parse(null, null)).isNull();
        for (Class<?> wrapper : List.of(SelectWrapper.class, InsertWrapper.class, UpdateWrapper.class,
                DeleteWrapper.class, CountWrapper.class, GroupWrapper.class)) {
            if (wrapper == SelectWrapper.class) continue;
            assertThat(actual.parseMap.get(wrapper).getClass()).isEqualTo(shared.parseMap.get(wrapper).getClass());
        }
        for (var entry : shared.parseMap.entrySet()) {
            if (entry.getKey() == com.yulinlin.data.core.node.AbstractMetaNode.class
                    || entry.getKey() == com.yulinlin.jdbc.sql.SqlPage.class
                    || entry.getKey() == SelectWrapper.class
                    || entry.getKey() == Match.class
                    || entry.getKey() == AsField.class) continue;
            assertThat(actual.parseMap.get(entry.getKey()).getClass()).isEqualTo(entry.getValue().getClass());
        }
        assertThat(actual.nameParse()).isExactlyInstanceOf(com.yulinlin.jdbc.postgresql.parse.NameParse.class);
        assertThat(actual.parseMap.get(com.yulinlin.jdbc.sql.SqlPage.class)).isExactlyInstanceOf(
                com.yulinlin.jdbc.postgresql.parse.PageParse.class);
        assertThat(actual.parseMap.get(DateGroup.class)).isExactlyInstanceOf(
                com.yulinlin.jdbc.postgresql.parse.group.DateParse.class);
        assertThat(actual.parseMap.get(IntervalGroup.class)).isExactlyInstanceOf(
                com.yulinlin.jdbc.postgresql.parse.group.IntervalParse.class);
        assertThat(actual.parseMap.get(Match.class)).isExactlyInstanceOf(
                com.yulinlin.jdbc.postgresql.parse.base.MatchParse.class);
        assertThat(actual.parseMap.get(AsField.class)).isExactlyInstanceOf(
                com.yulinlin.jdbc.postgresql.parse.select.AsFieldParse.class);
    }

    @Test void quotesSchemaColumnsCamelCaseAliasesAndPagesWithLock() {
        var query = new SelectWrapper<>().table("public.User", "u").page(2, 10).lock().orderBy("u.userName", true);
        query.fields().field("u.userName", "displayName");
        query.where().eq("u.id", "1").in("u.status", List.of(1, 2));
        var parsed = node(pg, query, RequestType.select);
        assertThat(sql(parsed)).contains("select \"u\".\"userName\" as \"displayName\"",
                "from \"public\".\"User\" \"u\"", "\"u\".\"id\" =", "\"u\".\"status\" in",
                "order by \"u\".\"userName\" asc", "LIMIT 10 OFFSET 10 for update").doesNotContain("`", "LIMIT 10,");
        assertThat(parsed.getList()).containsExactly("1", 1, 2);
        var names = ((SqlParseManager) pg.getParseManager()).nameParse();
        assertThat(names.alias("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(names.reference("u.*")).isEqualTo("\"u\".*");
    }

    @Test void standalonePostgresqlParserWorksWithoutCreatingSessionOrConnection() {
        var manager = new PostgresqlParseManager();
        var query = new SelectWrapper<>().table("users").page(2, 10);
        query.fields().field("id", "id");
        var parsed = (SqlNode) ((ParseResult) manager.parse(query, params(RequestType.select))).getRequest();
        assertThat(sql(parsed)).contains("\"id\" as \"id\"", "from \"users\"", "LIMIT 10 OFFSET 10");
        assertThat(manager.parse(new DateGroup("createdAt", DateGroup.Type.day), params(RequestType.group)).toString())
                .contains("date_trunc('day', CAST(\"createdAt\" AS timestamp)");
    }

    @Test void preservesMappedColumnsInPredicatesAndOrderBy() {
        var query = new SelectWrapper<>().table("users").orderBy("userName", true);
        query.fields().field("userName", "userName");
        query.where().eq("userName", "alice");
        var context = new SimpParamsContext(RequestType.select, Map.of(),
                new JdbcCoderManager().createEncoderBuffer(), Mapped.class, true);
        var parsed = (SqlNode) ((ParseResult) pg.parseSql(query, context)).getRequest();
        assertThat(sql(parsed)).contains("\"user_name\" as \"userName\"", "\"user_name\" =", "order by \"user_name\" asc");
    }

    @Test void nativeMatchAndHighlightReplaceTheOriginalFieldOnlyForIndexedProperties() {
        var query = new SelectWrapper<FullTextVideo>().table("video");
        query.fields().field("title", "title").field("author", "author");
        query.highlight("title").highlight("author");
        query.where().match("title", "Java 性能").match("author", "Alice");
        var context = new SimpParamsContext(RequestType.select, Map.of(),
                new JdbcCoderManager().createEncoderBuffer(), FullTextVideo.class, true);
        var parsed = (SqlNode) ((ParseResult) pg.parseSql(query, context)).getRequest();

        assertThat(sql(parsed))
                .contains("ts_headline('jiebacfg'::regconfig, COALESCE(\"video_title\", '')")
                .contains("to_tsvector('jiebacfg'::regconfig, COALESCE(\"video_title\", '')) @@ websearch_to_tsquery('jiebaqry'::regconfig")
                .contains("\"author\" as \"author\"", "\"author\" like")
                .doesNotContain("ts_headline('jiebacfg'::regconfig, COALESCE(\"author\"");
        assertThat(parsed.getList()).contains("Java 性能", "%Alice%")
                .anyMatch(value -> value.toString().contains("StartSel=\"__HL_START__\""));
    }

    @Test void sharedJdbcMatchFallsBackToLikeAndHighlightIsANoOp() {
        var shared = new JdbcSession(null);
        var query = new SelectWrapper<FullTextVideo>().table("video");
        query.fields().field("title", "title");
        query.highlight("title");
        query.where().match("title", "Java");
        var context = new SimpParamsContext(RequestType.select, Map.of(),
                new JdbcCoderManager().createEncoderBuffer(), FullTextVideo.class, true);
        var parsed = (SqlNode) ((ParseResult) shared.parseSql(query, context)).getRequest();
        assertThat(sql(parsed)).contains("video_title as `title`", "video_title like")
                .doesNotContain("ts_headline", "to_tsvector");
        assertThat(parsed.getList()).containsExactly("%Java%");
    }

    @Test void fullTextHighlightOptionsCanBeConfiguredWithoutEmbeddingTagsInSql() {
        var properties = new PostgresqlProperties();
        properties.getFullText().setIndexConfig("public.jiebacfg");
        properties.getFullText().setQueryConfig("public.jiebaqry");
        properties.getHighlight().setStartTag("<mark>");
        properties.getHighlight().setEndTag("</mark>");
        var session = new PostgresqlSession(null);
        session.configure(properties);
        var query = new SelectWrapper<FullTextVideo>().table("video");
        query.fields().field("title", "title");
        query.highlight("title");
        query.where().match("title", "中文搜索");
        var context = new SimpParamsContext(RequestType.select, Map.of(),
                new JdbcCoderManager().createEncoderBuffer(), FullTextVideo.class, true);
        var parsed = (SqlNode) ((ParseResult) session.parseSql(query, context)).getRequest();
        assertThat(sql(parsed)).contains("'public.jiebacfg'::regconfig", "'public.jiebaqry'::regconfig")
                .doesNotContain("<mark>", "</mark>");
        assertThat(parsed.getList()).anyMatch(value -> value.toString().contains("StartSel=\"<mark>\""));
    }

    @Test void insertsUpdatesAndDeletesKeepBoundParameters() {
        var insert = new InsertWrapper<>().table("public.users");
        insert.fields().field("id", "1").field("name", "Robert'); DROP TABLE users;--");
        var parsed = node(pg, insert, RequestType.insert);
        assertThat(sql(parsed)).startsWith("insert into \"public\".\"users\"").contains("\"id\"", "\"name\"")
                .doesNotContain("Robert", "`");
        assertThat(parsed.getList()).containsExactlyInAnyOrder("1", "Robert'); DROP TABLE users;--");
        var update = new UpdateWrapper<>().table("public.users");
        update.fields().field("name", "new").inc("score", 2);
        update.where().eq("users.id", "1");
        assertThat(sql(node(pg, update, RequestType.update))).contains("update \"public\".\"users\" set",
                "\"name\" =", "\"score\" = \"score\" +", "where \"id\" =").doesNotContain("\"users\".\"id\"");
        var delete = new DeleteWrapper<>().table("public.users");
        delete.where().eq("id", "1");
        assertThat(sql(node(pg, delete, RequestType.delete))).contains("delete from \"public\".\"users\"", "\"id\" =");
    }

    @ParameterizedTest @EnumSource(DateGroup.Type.class)
    void dateGroupingUsesPostgresqlFunctions(DateGroup.Type type) {
        String expression = pg.parseSql(new DateGroup("createdAt", type), params(RequestType.group)).toString();
        assertThat(expression).contains("to_char(date_trunc('" + type.name() + "', CAST(\"createdAt\" AS timestamp)")
                .doesNotContain("DATE_FORMAT", "MONTH(");
    }

    @Test void numericIntervalsAvoidIntegerDivisionAndRejectZero() {
        assertThat(pg.parseSql(new IntervalGroup("score", 10), params(RequestType.group)))
                .isEqualTo("(FLOOR(CAST(\"score\" AS numeric) / 10) + 1) * 10");
        assertThatThrownBy(() -> pg.parseSql(new IntervalGroup("score", 0), params(RequestType.group)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void groupedHavingExpandsMetricExpressionsAndCountsGroups() {
        var group = new GroupWrapper<>().table("users").page(2, 5).orderBy("totalCount", false);
        group.aggregations().field("status", "statusGroup");
        group.metrics().count("*", "totalCount");
        group.having().gt("totalCount", 1);
        assertThat(sql(node(pg, group, RequestType.group))).contains("count(*) as \"totalCount\"",
                "group by \"status\"", "having count(*) >", "order by \"totalCount\" desc",
                "LIMIT 5 OFFSET 5").doesNotContain("having \"totalCount\"");
        assertThat(sql(node(pg, new CountWrapper(group), RequestType.count)))
                .startsWith("select count(1) as total from (").contains("having count(*) >").doesNotContain("LIMIT");
    }

    @Test void jsonPathsAndNestedConditionsSupportTextNumbersBooleansAndArrays() {
        var query = new SelectWrapper<>().table("users");
        query.fields().field("payload->profile->name", "nickname");
        query.where().eq("payload->profile->name", "alice").gte("payload->age", 18)
                .eq("payload->enabled", true).in("payload->scores->0", List.of(1, 2));
        var parsed = node(pg, query, RequestType.select);
        assertThat(sql(parsed)).contains("CAST(\"payload\" AS jsonb) #>> ARRAY['profile', 'name']",
                "ARRAY['age']) AS numeric)", "ARRAY['enabled']) AS boolean)", "ARRAY['scores', '0']) AS numeric)")
                .doesNotContain("JSON_EXTRACT");
        assertThat(parsed.getList()).containsExactly("alice", 18, true, 1, 2);
        var nested = new SelectWrapper<>().table("users");
        nested.fields().field("id", "id");
        nested.where().nested("payload", c -> c.eq("name", "bob"));
        assertThat(sql(node(pg, nested, RequestType.select))).contains("ARRAY['name']");
        assertThat(SqlJsonUtil.isEmpty()).isTrue();
    }

    @Test void jsonKeysAreEscapedAndFailureDoesNotLeakPathToNextQuery() {
        assertThat(pg.parseSql(new Eq("payload->o'reilly", "x"), params(RequestType.select)).toString()).contains("'o''reilly'");
        var bad = new SelectWrapper<>().table("users");
        bad.fields().field("id", "id");
        bad.where().nested("payload", c -> c.eq("->broken", 1));
        assertThatThrownBy(() -> node(pg, bad, RequestType.select)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SqlJsonUtil.isEmpty()).isTrue();
        assertThat(pg.parseSql(new Eq("name", "x"), params(RequestType.select)).toString()).startsWith("\"name\" =");
        var update = new UpdateWrapper<>().table("users");
        update.fields().field("payload->name", "new");
        update.where().eq("id", "1");
        assertThatThrownBy(() -> node(pg, update, RequestType.update)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void defaultJdbcPagingAndAliasesRemainUnchanged() {
        var shared = new JdbcSession(null);
        var query = new SelectWrapper<>().table("users").page(2, 10);
        query.fields().field("name", "userName");
        assertThat(sql(node(shared, query, RequestType.select))).contains("name as `userName`", "LIMIT 10, 10");
    }

    @Test void normalRequestParsingUsesRegisteredParsersAndDoesNotOpenConnection() throws Exception {
        var dataSource = mock(javax.sql.DataSource.class);
        var session = new ProbeSession(dataSource);
        session.setCoderManager(new JdbcCoderManager());
        var query = new SelectWrapper<>().table("users").page(2, 10);
        query.fields().field("id", "id");
        var parsed = session.request(query, RequestType.select);
        assertThat(sql((SqlNode) parsed.getRequest())).contains("\"id\" as \"id\"", "LIMIT 10 OFFSET 10");
        verify(dataSource, never()).getConnection();
    }

    @Test void inheritedQueryExecutionUsesSharedObjectBindingAndBooleanReading() throws Exception {
        var connection = mock(java.sql.Connection.class);
        var statement = mock(PreparedStatement.class);
        var rows = mock(java.sql.ResultSet.class);
        var metadata = mock(java.sql.ResultSetMetaData.class);
        var buffer = new JdbcCoderManager().createDecoderBuffer().put("data", "payload");
        var query = new SqlNode("select enabled from users where payload = #{data}", buffer);
        when(connection.prepareStatement(query.getSql())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("enabled");
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.BOOLEAN);
        when(rows.next()).thenReturn(true, false);
        when(rows.getBoolean("enabled")).thenReturn(false);
        when(rows.wasNull()).thenReturn(false);
        pg.setCoderManager(new JdbcCoderManager());
        var result = pg.executeSelectNode(connection, query);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().<Boolean>getObject("enabled")).isFalse();
        verify(statement).setObject(1, "payload");
        verify(rows, never()).getString("enabled");
        verify(rows).close();
        verify(statement).close();
    }

    @Test void inheritedBatchExecutionUsesSharedObjectBindingAndBatchSize() throws Exception {
        var connection = mock(java.sql.Connection.class);
        var statement = mock(PreparedStatement.class);
        var buffer = new JdbcCoderManager().createDecoderBuffer().put("data", "payload");
        var insert = new SqlNode("insert into users(payload) values(#{data})", buffer);
        when(connection.prepareStatement(insert.getSql())).thenReturn(statement);
        when(statement.executeBatch()).thenReturn(new int[]{1});
        var request = new ParseResult(com.yulinlin.data.core.parse.ParseType.insert, insert, params(RequestType.insert));
        var session = new ProbeSession(null);
        session.setExecuteBatchSize(1);
        assertThat(session.write(connection, List.of(request, request))).isEqualTo(2);
        verify(connection).prepareStatement(insert.getSql());
        verify(statement, times(2)).setObject(1, "payload");
        verify(statement, times(2)).executeBatch();
        verify(statement, times(2)).clearBatch();
        verify(connection, never()).commit();
        verify(statement).close();
    }

    private static class ProbeSession extends PostgresqlSession {
        ProbeSession(javax.sql.DataSource dataSource) { super(dataSource); }
        ParseResult request(INode node, RequestType type) { return parseNode(type, Map.of(), node, Map.class); }
        int write(java.sql.Connection connection, List<ParseResult> nodes) { return executeUpdateNode(connection, nodes); }
    }

    @Test void parserRegistrationsDoNotLeakBetweenConcurrentDatabaseRequests() throws Exception {
        var shared = new PostgresqlParseManager();
        pg.setParseManager(shared);
        var otherPg = new PostgresqlSession(null);
        otherPg.setParseManager(shared);
        var jdbc = new JdbcSession(null);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<String>>();
            for (int i = 0; i < 80; i++) {
                boolean postgres = i % 2 == 0;
                JdbcSession selected = postgres ? (i % 4 == 0 ? pg : otherPg) : jdbc;
                tasks.add(() -> {
                    var query = new SelectWrapper<>().table("users").page(2, 3);
                    query.fields().field("id", "id");
                    return sql(node(selected, query, RequestType.select));
                });
            }
            var results = workers.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) assertThat(results.get(i).get())
                    .endsWith(i % 2 == 0 ? "LIMIT 3 OFFSET 3" : "LIMIT 3, 3");
        }
    }

    @Test void selectAliasesAreRequestLocalAndDoNotMutateCachedModelMapping() {
        var context = params(RequestType.select);
        var query = new SelectWrapper<>().table("users");
        query.fields().field("name", "temporaryAlias");
        pg.parseSql(query, context);
        assertThat(context.toColumn("temporaryAlias")).isEqualTo("temporaryAlias");
        assertThat(pg.parseSql(new Eq("temporaryAlias", "value"), context).toString()).startsWith("\"temporaryAlias\" =");
    }

    public static class Mapped {
        @JoinField(name = "user_name") private String userName;
        public String getUserName() { return userName; }
        public void setUserName(String value) { userName = value; }
    }

    public static class FullTextVideo {
        @JoinField(name = "video_title", fullText = true) private String title;
        private String author;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
    }

    @Test void nativeBooleanReadsFalseTrueAndNullWithoutStringCodecAmbiguity() throws Exception {
        var rows = mock(java.sql.ResultSet.class);
        when(rows.getBoolean("enabled")).thenReturn(false, true, false);
        when(rows.wasNull()).thenReturn(false, false, true);
        assertThat(pg.readColumn(rows, "enabled", java.sql.Types.BOOLEAN)).isEqualTo(false);
        assertThat(pg.readColumn(rows, "enabled", java.sql.Types.BOOLEAN)).isEqualTo(true);
        assertThat(pg.readColumn(rows, "enabled", java.sql.Types.BOOLEAN)).isNull();
        when(rows.getString("name")).thenReturn("f");
        assertThat(pg.readColumn(rows, "name", java.sql.Types.VARCHAR)).isEqualTo("f");
    }

    @Test void groupingAliasMatchingSourceColumnStillGroupsByDateBucket() {
        var group = new GroupWrapper<>().table("users");
        group.aggregations().day("created_at", "created_at");
        group.metrics().count("*", "totalCount");
        assertThat(sql(node(pg, group, RequestType.group))).contains("group by to_char(date_trunc('day'")
                .doesNotContain("group by \"created_at\"");
    }

    @Test void jsonLikeWithNumberUsesTextNotNumericOperand() {
        var query = new SelectWrapper<>().table("users");
        query.fields().field("id", "id");
        query.where().getCondition().and(new com.yulinlin.data.core.node.base.Like("payload->code", 12));
        var parsed = node(pg, query, RequestType.select);
        assertThat(sql(parsed)).contains("ARRAY['code']) like").doesNotContain("AS numeric");
        assertThat(parsed.getList()).containsExactly("%12%");
    }
}
