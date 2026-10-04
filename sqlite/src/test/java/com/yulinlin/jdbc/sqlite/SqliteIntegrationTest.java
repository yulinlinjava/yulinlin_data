package com.yulinlin.jdbc.sqlite;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.common.model.*;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.jdbc.mysql.MysqlParseAutoConfig;
import com.yulinlin.jdbc.mysql.MysqlParseManager;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import com.yulinlin.jdbc.session.JdbcSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SqliteIntegrationTest {
    @TempDir Path directory;
    @org.junit.jupiter.api.AfterEach void clearStaticRouteCache() {
        // Every test owns a new context; the framework's per-thread route cache outlives it.
        if (SessionUtil.route() != null) SessionUtil.route().clear();
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(TestInfrastructure.class, BootConfiguration.class);
    }

    @Test void fileOnlySupportsOrmBatchAndTransactions() {
        Path file = directory.resolve("nested/local.db");
        runner().withUserConfiguration(Services.class)
                .withPropertyValues("yulinlin.sqlite.file=" + file).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(javax.sql.DataSource.class);
            assertThat(context).doesNotHaveBean(org.springframework.transaction.PlatformTransactionManager.class);
            var ds = context.getBean(SqliteDatabase.class).dataSource();
            JdbcTemplate sql = new JdbcTemplate(ds);
            assertThat(sql.queryForObject("PRAGMA journal_mode", String.class)).isEqualTo("wal");
            assertThat(sql.queryForObject("PRAGMA synchronous", Integer.class)).isEqualTo(1);
            assertThat(sql.queryForObject("PRAGMA busy_timeout", Integer.class)).isEqualTo(5000);
            assertThat(sql.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
            assertThat(file).exists();
            sql.execute("create table local_user(id text primary key, user_name text, status integer)");
            assertThat(SessionUtil.route().loadBalanceList()).containsExactly("local");
            assertThat(ModelInsertWrapper.newInstance("local", List.of(user("1", "alice"), user("2", "bob"))).execute()).isEqualTo(2);
            var page = ModelSelectWrapper.newInstance("local", User.class).orderByAsc(User::getId).selectPage(1, 1);
            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getList()).extracting(User::getName).containsExactly("alice");
            assertThat(ModelSelectWrapper.newInstance("local", User.class).like(User::getName, "lic").count()).isEqualTo(1);
            User patch = new User(); patch.setId("1"); patch.setStatus(2);
            assertThat(ModelUpdateWrapper.newInstance("local", patch).execute()).isEqualTo(1);
            assertThat(ModelSelectWrapper.newInstance("local", User.class).eq(User::getStatus, 2).selectOne().getId()).isEqualTo("1");
            assertThatThrownBy(() -> ModelInsertWrapper.newInstance("local", List.of(user("3", "new"), user("1", "duplicate"))).execute())
                    .isInstanceOf(Exception.class);
            assertThat(sql.queryForObject("select count(*) from local_user where id='3'", Integer.class)).isZero();
            assertThatThrownBy(() -> context.getBean(WriteService.class).fail()).isInstanceOf(IllegalStateException.class);
            assertThat(sql.queryForObject("select count(*) from local_user where id='4'", Integer.class)).isZero();
            // Framework-only transaction, without Spring's DataSource transaction manager.
            assertThatThrownBy(() -> SessionUtil.route().transaction(() -> {
                ModelInsertWrapper.newInstance("local", user("5", "rollback")).execute();
                throw new IllegalArgumentException("rollback");
            })).isInstanceOf(IllegalArgumentException.class);
            assertThat(sql.queryForObject("select count(*) from local_user where id='5'", Integer.class)).isZero();
            assertThat(ModelDeleteWrapper.newInstance("local", user("2", "bob")).execute()).isEqualTo(1);
            sql.execute("create table child(id integer primary key, parent_id text references local_user(id))");
            assertThatThrownBy(() -> sql.update("insert into child values(1, 'missing')")).isInstanceOf(Exception.class);
        });
        SqliteProperties properties = new SqliteProperties(); properties.setFile(file.toString());
        try (var reopened = new SqliteDataSource(properties)) {
            assertThat(new JdbcTemplate(reopened).queryForObject("select count(*) from local_user", Integer.class)).isEqualTo(1);
        }
    }

    @Test void autoSchemaSupportsCodecRoundTripAndDateRanges() {
        runner().withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("auto.db"),
                "yulinlin.sqlite.schema.enabled=true",
                "yulinlin.sqlite.schema.packages[0]=com.yulinlin.jdbc.sqlite.fixtures").run(context -> {
            assertThat(context).hasNotFailed();
            var entity = new com.yulinlin.jdbc.sqlite.fixtures.SchemaEntity();
            entity.setId("1"); entity.setName("test");
            entity.setCreatedAt(com.yulinlin.data.lang.util.DateTime.parse("2026-10-04 12:30:00").toDate());
            entity.setAmount(new java.math.BigDecimal("12345678901234567890.123400"));
            entity.setState(com.yulinlin.jdbc.sqlite.fixtures.SchemaEntity.State.READY);
            entity.setDetails(java.util.Map.of("key", "value"));
            var payload = new com.yulinlin.jdbc.sqlite.fixtures.SchemaEntity.Payload();
            payload.setMessage("hello"); entity.setPayload(payload);
            entity.setBytes(new byte[]{1, 2, 3}); entity.setQuantity(7); entity.setRatio(1.5); entity.setEnabled(true);
            assertThat(ModelInsertWrapper.newInstance("local", entity).execute()).isEqualTo(1);
            var rows = ModelSelectWrapper.newInstance("local", com.yulinlin.jdbc.sqlite.fixtures.SchemaEntity.class)
                    .gte("createdAt", com.yulinlin.data.lang.util.DateTime.parse("2026-10-04 00:00:00").toDate())
                    .lt("createdAt", com.yulinlin.data.lang.util.DateTime.parse("2026-10-05 00:00:00").toDate()).selectList();
            assertThat(rows).hasSize(1);
            var copy = rows.getFirst();
            assertThat(copy.getCreatedAt()).isEqualTo(entity.getCreatedAt());
            assertThat(copy.getAmount()).isEqualTo(entity.getAmount());
            assertThat(copy.getState()).isEqualTo(entity.getState());
            assertThat(copy.getDetails()).isEqualTo(entity.getDetails());
            assertThat(copy.getPayload().getMessage()).isEqualTo("hello");
            assertThat(copy.getBytes()).containsExactly(1, 2, 3);
            assertThat(copy.getQuantity()).isEqualTo(7);
            assertThat(copy.getRatio()).isEqualTo(1.5);
            assertThat(copy.getEnabled()).isTrue();
            var sql = new JdbcTemplate(context.getBean(SqliteDatabase.class).dataSource());
            assertThat(sql.queryForObject("select typeof(amount) from schema_entity", String.class)).isEqualTo("text");
        });
    }

    @Test void genericJdbcSessionWorksWithoutRouterAndKeepsSqliteBatchesSingleConnection() {
        runner().withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("generic.db"),
                "yulinlin.datasource.jdbc.parallel-connections=4").run(context -> {
            assertThat(context).hasNotFailed();
            JdbcSession session = context.getBean("sqliteSession", JdbcSession.class);
            assertThat(session).isExactlyInstanceOf(JdbcSession.class);
            assertThat(session.getParallelConnections()).isEqualTo(1);
            assertThat(session.supportsParallelWrites()).isFalse();
            assertThat(session.getExecuteBatchSize()).isEqualTo(256);
            var sql = new JdbcTemplate(context.getBean(SqliteDatabase.class).dataSource());
            sql.execute("create table local_user(id text primary key, user_name text, status integer)");
            var request = ModelInsertWrapper.newInstance("local", user("standalone", "direct")).getRequest();
            var savedRoute = SessionUtil.route();
            new SessionUtil(null); // Executing a configured session does not depend on any global route.
            try {
                session.startTransaction();
                assertThat(session.insert(request)).isEqualTo(1);
                session.rollbackTransaction();
                assertThat(sql.queryForObject("select count(*) from local_user", Integer.class)).isZero();
                assertThat(session.insert(request)).isEqualTo(1);
            } finally { new SessionUtil(savedRoute); }
            var users = java.util.stream.IntStream.range(0, 512).mapToObj(i -> user("bulk-" + i, "batch")).toList();
            assertThat(ModelInsertWrapper.newInstance("local", users).batch().execute()).isEqualTo(512);
            assertThat(sql.queryForObject("select count(*) from local_user", Integer.class)).isEqualTo(513);
            var badBatch = new java.util.ArrayList<>(java.util.stream.IntStream.range(0, 512)
                    .mapToObj(i -> user("rollback-" + i, "bad batch")).toList());
            badBatch.add(user("standalone", "duplicate"));
            assertThatThrownBy(() -> ModelInsertWrapper.newInstance("local", badBatch).batch().execute()).isInstanceOf(Exception.class);
            assertThat(sql.queryForObject("select count(*) from local_user", Integer.class)).isEqualTo(513);
        });
    }

    @Test void schemaDisabledByDefaultAndEmptyEnabledScanFails() {
        runner().withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("disabled.db"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(new JdbcTemplate(context.getBean(SqliteDatabase.class).dataSource()).queryForObject(
                            "select count(*) from sqlite_schema where type='table'", Integer.class)).isZero();
                });
        runner().withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("invalid.db"),
                "yulinlin.sqlite.schema.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test void mysqlAndSqliteHaveIndependentFactoriesAndRouting() {
        runner()
                .withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("mixed.db"),
                        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/not_connected",
                        "spring.datasource.username=test", "spring.datasource.password=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("mysqlSessionFactory")).isExactlyInstanceOf(JdbcSessionFactory.class);
                    assertThat(context.getBean("sqliteSessionFactory")).isExactlyInstanceOf(JdbcSessionFactory.class);
                    assertThat(context.getBean("sqliteSession")).isExactlyInstanceOf(JdbcSession.class);
                    assertThat(context).hasSingleBean(javax.sql.DataSource.class).doesNotHaveBean(SqliteDataSource.class);
                    assertThat(context.getBean(javax.sql.DataSource.class)).isNotSameAs(context.getBean(SqliteDatabase.class).dataSource());
                    var mainTx = context.getBean("transactionManager", DataSourceTransactionManager.class);
                    assertThat(mainTx.getDataSource()).isSameAs(context.getBean(javax.sql.DataSource.class));
                    assertThat(SessionUtil.route().loadBalanceList()).containsExactlyInAnyOrder("primary", "local");
                    var ds = context.getBean(SqliteDatabase.class).dataSource();
                    var sql = new JdbcTemplate(ds);
                    sql.execute("create table local_user(id text primary key, user_name text, status integer)");
                    assertThat(ModelInsertWrapper.newInstance("local", user("1", "local")).execute()).isEqualTo(1);
                    assertThat(ModelSelectWrapper.newInstance("local", User.class).selectOne().getName()).isEqualTo("local");
                    assertThat(context).doesNotHaveBean("sqliteTransactionManager");
                    assertThatThrownBy(() -> SessionUtil.route().transaction(() -> {
                        ModelInsertWrapper.newInstance("local", user("2", "rollback")).execute();
                        throw new IllegalStateException("rollback");
                    })).isInstanceOf(IllegalStateException.class);
                    assertThat(sql.queryForObject("select count(*) from local_user", Integer.class)).isEqualTo(1);
                });
    }

    @Test void sqliteAloneWorksEvenWithMysqlAutoConfiguration() {
        runner()
                .withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("standalone.db"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class).doesNotHaveBean("jdbcSession");
                    assertThat(SessionUtil.route().loadBalanceList()).containsExactly("local");
                });
    }

    @Test void standaloneExplicitPrimaryGroupDoesNotCreateMysqlSessionForSqlite() {
        runner()
                .withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("local-only.db"),
                        "yulinlin.sqlite.group=primary")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class).doesNotHaveBean("jdbcSession");
                    assertThat(SessionUtil.route().loadBalanceList()).containsExactly("primary");
                });
    }

    @Test void multiSessionFrameworkTransactionCommitsAndRollsBack() {
        runner().withUserConfiguration(Services.class)
                .withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("multi.db"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class)
                            .doesNotHaveBean(org.springframework.transaction.PlatformTransactionManager.class);
                    var primary = new JdbcTemplate(context.getBean(SqliteDatabase.class).dataSource());
                    primary.execute("create table local_user(id text primary key, user_name text, status integer)");
                    var properties = new SqliteProperties();
                    properties.setFile(directory.resolve("backup.db").toString()); properties.setGroup("backup");
                    try (var database = new SqliteDatabase(properties)) {
                        var backup = new JdbcTemplate(database.dataSource());
                        backup.execute("create table local_user(id text primary key, user_name text, status integer)");
                        var session = context.getBean("sqliteSessionFactory", JdbcSessionFactory.class).create(database.dataSource(), "backup");
                        SessionUtil.route().registerSession(session);
                        var service = context.getBean(WriteService.class);
                        assertThatThrownBy(() -> service.writeBoth(true)).isInstanceOf(IllegalStateException.class);
                        assertThat(primary.queryForObject("select count(*) from local_user", Integer.class)).isZero();
                        assertThat(backup.queryForObject("select count(*) from local_user", Integer.class)).isZero();
                        service.writeBoth(false);
                        assertThat(primary.queryForObject("select count(*) from local_user", Integer.class)).isEqualTo(1);
                        assertThat(backup.queryForObject("select count(*) from local_user", Integer.class)).isEqualTo(1);
                    }
                });
    }

    @Test void internalPoolClosesWithContext() {
        var holder = new java.util.concurrent.atomic.AtomicReference<SqliteDataSource>();
        runner().withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("close.db"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    holder.set(context.getBean(SqliteDatabase.class).dataSource());
                    assertThat(holder.get().isClosed()).isFalse();
                });
        assertThat(holder.get().isClosed()).isTrue();
    }

    @Test void disabledWithoutFile() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SqliteAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(SqliteDataSource.class));
    }

    @Test void rejectsMemoryAndHonorsFullAndTimeout() {
        SqliteProperties p = new SqliteProperties(); p.setFile(":memory:");
        assertThatThrownBy(() -> new SqliteDataSource(p)).isInstanceOf(IllegalArgumentException.class);
        p.setFile(directory.resolve("full.db").toString()); p.setSynchronous(SqliteProperties.Sync.FULL); p.setBusyTimeout(1200);
        try (var ds = new SqliteDataSource(p)) {
            JdbcTemplate sql = new JdbcTemplate(ds);
            assertThat(sql.queryForObject("PRAGMA synchronous", Integer.class)).isEqualTo(2);
            assertThat(sql.queryForObject("PRAGMA busy_timeout", Integer.class)).isEqualTo(1200);
        }
    }

    @Test void explicitPrimaryGroupConflictFailsClearly() {
        runner().withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("conflict.db"),
                "yulinlin.sqlite.group=primary",
                "spring.datasource.url=jdbc:mysql://localhost/db").run(context -> assertThat(context).hasFailed());
    }

    @Test void sharesSqlParsersButRejectsRowLocks() {
        var mysql = new MysqlParseManager(); var sqlite = new SqliteParseManager();
        assertThatThrownBy(() -> sqlite.parse(new SelectWrapper<>().lock(), null))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("FOR UPDATE");
        assertThat(sqlite.parseMap.get(SelectWrapper.class).getClass()).isEqualTo(mysql.parseMap.get(SelectWrapper.class).getClass());
        // Legacy public parser entry points retain their generic node registration.
        assertThat(new com.yulinlin.jdbc.mysql.parse.mysql.MysqlSelectWrapperParse().getNodeClass()).isEqualTo(SelectWrapper.class);
    }

    @JoinTable("local_user")
    public static class User extends IdEntity<User> {
        @JoinField(name = "user_name") private String name;
        @JoinField private Integer status;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }
    static User user(String id, String name) { User u = new User(); u.setId(id); u.setName(name); u.setStatus(1); return u; }
    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructure {
        // SQLite tests do not need HTTP sockets or the application's shared background pools.
        @Bean com.yulinlin.data.core.http.HttpRequestClient httpRequestClient() {
            return new com.yulinlin.data.core.http.HttpRequestClient(
                    org.springframework.web.client.RestClient.builder().requestFactory(
                            new org.springframework.http.client.SimpleClientHttpRequestFactory()).build(),
                    new com.fasterxml.jackson.databind.ObjectMapper());
        }
        @Bean com.yulinlin.data.core.loadbalan.LoadBalance loadBalance() {
            return new com.yulinlin.data.core.loadbalan.RandomLoadBalance();
        }
        @Bean java.util.concurrent.ThreadPoolExecutor threadPoolExecutor() {
            return (java.util.concurrent.ThreadPoolExecutor) java.util.concurrent.Executors.newFixedThreadPool(1);
        }
        @Bean java.util.concurrent.ScheduledExecutorService scheduledExecutorService() {
            return java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        }
    }
    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class BootConfiguration {}
    @Configuration(proxyBeanMethods = false)
    static class Services {
        @Bean WriteService writeService() { return new WriteService(); }
    }
    public static class WriteService {
        @com.yulinlin.data.core.anno.JoinTransaction
        public void writeBoth(boolean fail) {
            ModelInsertWrapper.newInstance("local", user("both", "local")).execute();
            ModelInsertWrapper.newInstance("backup", user("both", "backup")).execute();
            if (fail) throw new IllegalStateException("rollback both");
        }
        @Transactional public void fail() {
            ModelInsertWrapper.newInstance("local", user("4", "spring")).execute();
            throw new IllegalStateException("rollback");
        }
    }
}
