package com.yulinlin.jdbc.sqlite;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.filter.IRequestFilter;
import com.yulinlin.data.core.model.BaseModel;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.impl.AbstractFieldsWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

/** Real SQLite, SQL generation, CGLIB proxies, route nesting and Spring transaction integration. */
class OrmProxyIntegrationTest {
    @TempDir Path directory;
    @AfterEach void clean() {
        if (SessionUtil.route() != null) {
            if (SessionUtil.route().isOpenTransaction()) SessionUtil.route().rollbackTransaction();
            SessionUtil.route().clear();
        }
    }

    private void run(Consumer<Fixture> test) {
        run(test, new String[0]);
    }

    private void run(Consumer<Fixture> test, String... additionalProperties) {
        String[] properties = new String[additionalProperties.length + 2];
        properties[0] = "yulinlin.sqlite.file=" + directory.resolve("proxy.db");
        properties[1] = "yulinlin.sqlite.group=local";
        System.arraycopy(additionalProperties, 0, properties, 2, additionalProperties.length);
        new ApplicationContextRunner()
                .withUserConfiguration(SqliteIntegrationTest.TestInfrastructure.class,
                        SqliteIntegrationTest.BootConfiguration.class, RecordingConfiguration.class)
                .withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var f = new Fixture(SessionUtil.route(),
                            new JdbcTemplate(context.getBean(SqliteDatabase.class).dataSource()),
                            context.getBean(Recorder.class), context.getBean("sqliteSessionFactory", JdbcSessionFactory.class));
                    schema(f.sql);
                    seed(f.sql);
                    try { test.accept(f); }
                    finally { if (f.route.isOpenTransaction()) f.route.rollbackTransaction(); }
                });
    }
    private static void schema(JdbcTemplate sql) {
        sql.execute("create table proxy_user(id text primary key, name text, age integer, enabled integer, status integer, tags text)");
        sql.execute("create table proxy_parent(id text primary key, child_id text)");
        sql.execute("create table proxy_version(id text primary key, name text, version integer)");
        sql.execute("create table proxy_hook(id text primary key, name text, updated integer)");
    }
    private static void seed(JdbcTemplate sql) {
        for (int i = 1; i <= 3; i++)
            sql.update("insert into proxy_user values(?,?,?,?,?,?)", "" + i, "user-" + i, 44, 1, 8, "[\"base\"]");
        sql.update("insert into proxy_parent values('a','1'),('b','2'),('c','1'),('d','missing'),('e',null)");
    }
    private record Fixture(RouteSession route, JdbcTemplate sql, Recorder recorder, JdbcSessionFactory factory) {
        User user(String id) { return ModelSelectWrapper.newInstance("local", User.class).eq("id", id).selectOne(); }
        List<Parent> parents() {
            return ModelSelectWrapper.newInstance("local", Parent.class).orderByAsc("id").selectList();
        }
        <E> E lazy(E bean) { return route.callable("local", () -> route.getLazyProxy(bean)); }
        <E> E sync(E bean) { return route.callable("local", () -> route.getSyncProxy(bean)); }
    }

    @Test void firstGetterLoadsWholeQueryBatchAndCachesMissingRows() {
        run(f -> f.route.transaction(() -> {
            List<Parent> rows = f.parents(); // Query path automatically creates the batch proxies.
            assertThat(f.recorder.userQueries()).isZero();
            assertThat(rows.get(0).getChild().getId()).isEqualTo("1");
            assertThat(f.recorder.userQueries()).isEqualTo(1);
            assertThat(rows.get(1).getChild().getId()).isEqualTo("2");
            assertThat(rows.get(2).getChild()).isSameAs(rows.get(0).getChild());
            assertThat(rows.get(3).getChild()).isNull();
            assertThat(rows.get(3).getChild()).isNull();
            assertThat(rows.get(4).getChild()).isNull();
            assertThat(f.recorder.userQueries()).isEqualTo(1);
            assertThat(rows.get(0).getComputedNull()).isNull();
            return null;
        }));
    }

    @Test void autoUpdateQueryRequiresAnActiveTransaction() {
        run(f -> assertThatThrownBy(() -> ModelSelectWrapper.newInstance("local", User.class)
                .autoUpdate().eq("id", "1").selectOne())
                .hasMessageContaining("请开启事务"));
    }

    @Test void globalAutoUpdateRequiresAnActiveTransaction() {
        run(f -> assertThatThrownBy(() -> f.user("1")).hasMessageContaining("请开启事务"),
                "yulinlin.data.auto-update=true");
    }

    @Test void globalAutoUpdateTracksSettersInsideTransaction() {
        run(f -> {
            f.route.transaction(() -> {
                f.user("1").setName("global");
                return null;
            });
            assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class))
                    .isEqualTo("global");
        }, "yulinlin.data.auto-update=true");
    }

    @Test void queryCanDisableGlobalAutoUpdate() {
        run(f -> {
            User user = ModelSelectWrapper.newInstance("local", User.class)
                    .autoUpdate(false).eq("id", "1").selectOne();
            user.setName("memory-only");
            assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class))
                    .isEqualTo("user-1");
        }, "yulinlin.data.auto-update=true");
    }

    @Test void chunkedInQueriesDistributeListsAndKeepOrdering() {
        run(f -> f.route.transaction(() -> {
            List<ChunkRelations> rows = f.lazy(List.of(chunk("1", "3"), chunk("2", "1"), chunk("missing"), chunk()));
            assertThat(rows.get(0).getUsers()).extracting(User::getId).containsExactly("3", "1");
            assertThat(rows.get(1).getUsers()).extracting(User::getId).containsExactly("2", "1");
            assertThat(rows.get(2).getUsers()).isEmpty();
            assertThat(rows.get(3).getUsers()).isEmpty();
            assertThat(f.recorder.userQueries()).isEqualTo(2); // Four unique keys, not five parent/key pairs.
            return null;
        }));
    }
    @Test void eagerBatchAlsoUsesChunkedIndexDistribution() {
        run(f -> {
            EagerRelations a = new EagerRelations(); a.setIds(List.of("1", "3"));
            EagerRelations b = new EagerRelations(); b.setIds(List.of("2", "1"));
            List<EagerRelations> rows = f.lazy(List.of(a, b));
            assertThat(rows.get(0).getUsers()).extracting(User::getId).containsExactly("1", "3");
            assertThat(rows.get(1).getUsers()).extracting(User::getId).containsExactly("1", "2");
            assertThat(f.recorder.userQueries()).isEqualTo(2);
        });
    }
    @Test void lazyDependencyChainsLoadEachFieldOnceForWholeBatch() {
        run(f -> f.route.transaction(() -> {
            Chain a = new Chain(); a.setIds(List.of("1"));
            Chain b = new Chain(); b.setIds(List.of("2"));
            List<Chain> rows = f.lazy(List.of(a, b));
            assertThat(rows.get(0).getCopies()).extracting(User::getId).containsExactly("1");
            assertThat(rows.get(1).getCopies()).extracting(User::getId).containsExactly("2");
            assertThat(f.recorder.userQueries()).isEqualTo(2);
            return null;
        }));
    }
    @Test void cyclesFailClearlyAndFailedLoadCanRetry() {
        run(f -> f.route.transaction(() -> {
            Circular circular = f.lazy(new Circular());
            assertThatThrownBy(circular::getChild).hasMessageContaining("循环懒加载");
            List<Parent> rows = f.parents();
            f.recorder.failNextUserQuery = true;
            assertThatThrownBy(rows.getFirst()::getChild).hasMessageContaining("query failure");
            assertThat(rows.getFirst().getChild().getId()).isEqualTo("1");
            assertThat(f.recorder.userQueries()).isEqualTo(2);
            return null;
        }));
    }
    @Test void presetOrExplicitlyAssignedRelationsAreNotOverwritten() {
        run(f -> f.route.transaction(() -> {
            Parent preset = parent("a", "1"); User custom = new User(); custom.setName("preset"); preset.setChild(custom);
            Parent pending = parent("b", "2"); Parent assigned = parent("c", "3");
            List<Parent> rows = f.lazy(List.of(preset, pending, assigned));
            rows.get(2).setChild(null); // Explicit assignment cancels automatic loading for this object.
            assertThat(rows.get(1).getChild().getId()).isEqualTo("2");
            assertThat(rows.get(0).getChild()).isSameAs(custom);
            assertThat(rows.get(2).getChild()).isNull();
            assertThat(f.recorder.userQueries()).isEqualTo(1);
            return null;
        }));
    }
    @Test void unloadedProxyCannotCrossTransactionOrThread() {
        run(f -> {
            Parent old = f.route.transaction(() -> f.lazy(parent("a", "1")));
            assertThatThrownBy(old::getChild).hasMessageContaining("原始事务");
            f.route.transaction(() -> {
                assertThatThrownBy(old::getChild).hasMessageContaining("原始事务");
                Parent current = f.lazy(parent("b", "2"));
                try (var executor = Executors.newSingleThreadExecutor()) {
                    assertThatThrownBy(() -> executor.submit(current::getChild).get()).hasRootCauseMessage(
                            "懒加载必须在创建代理的线程及原始事务内执行: child");
                }
                return null;
            });
            Parent loaded = f.route.transaction(() -> {
                Parent row = f.lazy(parent("a", "1")); row.getChild(); return row;
            });
            assertThat(loaded.getChild().getId()).isEqualTo("1"); // Loaded data remains readable after completion.
        });
    }
    @Test void lazyAndSyncKeepOriginalSourceWhenAmbientSourceChanges() {
        run(f -> {
            SqliteProperties properties = new SqliteProperties();
            properties.setFile(directory.resolve("backup.db").toString()); properties.setGroup("backup");
            try (var db = new SqliteDatabase(properties)) {
                JdbcTemplate backup = new JdbcTemplate(db.dataSource()); schema(backup); seed(backup);
                backup.update("update proxy_user set name='backup'");
                var session = f.factory.create(db.dataSource(), "backup");
                f.route.registerSession(session);
                try {
                    f.route.transaction(() -> {
                        Parent original = f.parents().getFirst();
                        User local = f.sync(f.user("1"));
                        f.route.callable("backup", () -> {
                            assertThat(original.getChild().getName()).isEqualTo("user-1");
                            local.setName("local-only");
                            return null;
                        });
                        return null;
                    });
                    assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("local-only");
                    assertThat(backup.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("backup");
                } finally { f.route.remove(session); }
            }
        });
    }
    @Test void dirtyPatchDoesNotWriteUntouchedDefaultsAndSetterRunsOnce() {
        run(f -> {
            User partial = new User(); partial.setId("1");
            f.route.transaction(() -> {
                User proxy = f.sync(partial);
                proxy.setName("changed");
                assertThat(proxy.getSetterCalls()).isEqualTo(1);
                proxy.setUp(); // Not a property setter and must not inspect args[0].
                proxy.getTags().add("ignored");
                return null;
            });
            assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("changed");
            assertThat(f.sql.queryForObject("select age from proxy_user where id='1'", Integer.class)).isEqualTo(44);
            assertThat(f.sql.queryForObject("select enabled from proxy_user where id='1'", Integer.class)).isEqualTo(1);
            assertThat(f.sql.queryForObject("select status from proxy_user where id='1'", Integer.class)).isEqualTo(8);
            assertThat(f.sql.queryForObject("select tags from proxy_user where id='1'", String.class)).isEqualTo("[\"base\"]");
            assertThat(f.recorder.updatedFields).containsExactly(Set.of("name"));
        });
    }
    @Test void explicitZeroFalseAndNullSkippingKeepTheirSemantics() {
        run(f -> {
            f.route.transaction(() -> {
                User proxy = f.sync(f.user("1"));
                proxy.setAge(0); proxy.setEnabled(false); proxy.setName("temporary"); proxy.setName(null);
                return null;
            });
            assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("user-1");
            assertThat(f.sql.queryForObject("select age from proxy_user where id='1'", Integer.class)).isZero();
            assertThat(f.sql.queryForObject("select enabled from proxy_user where id='1'", Integer.class)).isZero();
            assertThat(f.recorder.updatedFields).containsExactly(Set.of("age", "enabled"));
        });
    }
    @Test void nestedCommitDoesNotLoseLaterChangesAndRollbackDiscardsEverything() {
        run(f -> {
            f.route.transaction(() -> {
                User proxy = f.sync(f.user("1")); proxy.setName("before");
                f.route.transaction(() -> { proxy.setStatus(9); return null; });
                assertThat(f.recorder.updatedFields).isEmpty();
                proxy.setName("after");
                return null;
            });
            assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("after");
            assertThat(f.sql.queryForObject("select status from proxy_user where id='1'", Integer.class)).isEqualTo(9);
            assertThatThrownBy(() -> f.route.transaction(() -> {
                User proxy = f.sync(f.user("2")); proxy.setName("must-rollback");
                assertThatThrownBy(() -> f.route.transaction(() -> { throw new IllegalArgumentException("inner"); }))
                        .isInstanceOf(IllegalArgumentException.class);
                proxy.setStatus(0);
                return null;
            })).hasMessageContaining("rollback-only");
            assertThat(f.sql.queryForObject("select name from proxy_user where id='2'", String.class)).isEqualTo("user-2");
        });
    }
    @Test void identityCacheDoesNotMergeEqualEntitiesOrDependOnMutableHashCode() {
        run(f -> f.route.transaction(() -> {
            User a = f.user("1"), b = f.user("1");
            assertThat(a).isEqualTo(b);
            User pa = f.sync(a), pb = f.sync(b);
            assertThat(pa).isNotSameAs(pb);
            pa.setName("changed-hash");
            assertThat(f.sync(a)).isSameAs(pa);
            assertThat(pb.getName()).isEqualTo("user-1");
            return null;
        }));
    }
    @Test void sameObjectCanHaveIndependentSetterPatchesForTwoSources() {
        run(f -> {
            SqliteProperties properties = new SqliteProperties();
            properties.setFile(directory.resolve("independent.db").toString()); properties.setGroup("backup");
            try (var db = new SqliteDatabase(properties)) {
                var backup = new JdbcTemplate(db.dataSource()); schema(backup); seed(backup);
                backup.update("update proxy_user set name='backup-original'");
                var session = f.factory.create(db.dataSource(), "backup"); f.route.registerSession(session);
                try {
                    f.route.transaction(() -> {
                        assertThat(ModelSelectWrapper.newInstance("local", User.class).cache().eq("id", "1").selectOne().getName())
                                .isEqualTo("user-1");
                        assertThat(ModelSelectWrapper.newInstance("backup", User.class).cache().eq("id", "1").selectOne().getName())
                                .isEqualTo("backup-original");
                        User raw = f.user("1");
                        User local = f.sync(raw);
                        User other = f.route.callable("backup", () -> f.route.getSyncProxy(raw));
                        assertThat(local).isNotSameAs(other);
                        local.setName("local"); other.setName("backup");
                        return null;
                    });
                    assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("local");
                    assertThat(backup.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("backup");
                } finally { f.route.remove(session); }
            }
        });
    }
    @Test void writesToOriginalObjectDoNotReplaceTrackedSetterValues() {
        run(f -> {
            User raw = new User(); raw.setId("1");
            f.route.transaction(() -> {
                User proxy = f.sync(raw); proxy.setName("tracked"); raw.setName("untracked");
                return null;
            });
            assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("tracked");
        });
    }
    @Test void primaryKeySafetyAndOldSyncProxyAreEnforced() {
        run(f -> {
            User old = f.route.transaction(() -> {
                assertThatThrownBy(() -> f.sync(new User())).hasMessageContaining("非空主键");
                assertThatThrownBy(() -> f.sync(new NoKey())).hasMessageContaining("缺少JoinMeta");
                User proxy = f.sync(f.user("1"));
                assertThatThrownBy(() -> proxy.setId("2")).hasMessageContaining("不允许修改主键");
                proxy.setName("safe");
                return proxy;
            });
            assertThat(f.sql.queryForObject("select name from proxy_user where id='2'", String.class)).isEqualTo("user-2");
            assertThatThrownBy(() -> old.setName("lost")).hasMessageContaining("原始事务");
            f.route.transaction(() -> {
                assertThatThrownBy(() -> old.setName("lost")).hasMessageContaining("原始事务");
                return null;
            });
        });
    }
    @Test void autoUpdateQueryPropagatesToLazyAssociations() {
        run(f -> {
            f.route.transaction(() -> {
                Parent parent = ModelSelectWrapper.newInstance("local", Parent.class)
                        .autoUpdate().eq("id", "a").selectOne();
                parent.getChild().setName("association");
                return null;
            });
            assertThat(f.sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("association");
            assertThat(f.recorder.updatedFields).containsExactly(Set.of("name"));
        });
    }
    @Test void updateBeforeAndOptimisticVersionArePreserved() {
        run(f -> {
            f.sql.update("insert into proxy_hook values('1','old',0)");
            f.sql.update("insert into proxy_version values('1','old',3)");
            f.route.transaction(() -> {
                HookUser user = new HookUser(); user.setId("1"); f.sync(user).setName("new");
                VersionUser version = new VersionUser(); version.setId("1"); version.setVersion(3); f.sync(version).setName("new");
                return null;
            });
            assertThat(f.sql.queryForObject("select updated from proxy_hook", Integer.class)).isEqualTo(1);
            assertThat(f.sql.queryForObject("select version from proxy_version", Integer.class)).isEqualTo(4);
            assertThatThrownBy(() -> f.route.transaction(() -> {
                VersionUser stale = new VersionUser(); stale.setId("1"); stale.setVersion(3); f.sync(stale).setName("stale");
                return null;
            })).hasMessageContaining("乐观锁");
            assertThat(f.sql.queryForObject("select name from proxy_version", String.class)).isEqualTo("new");
        });
    }
    @Test void cacheContextDoesNotLeakFromPreviousQuery() {
        run(f -> f.route.transaction(() -> {
            List<Parent> cached = ModelSelectWrapper.newInstance("local", Parent.class).cache().selectList();
            cached.getFirst().getChild();
            assertThat(f.recorder.userCacheFlags).containsExactly(true);
            f.parents().getFirst().getChild();
            assertThat(f.recorder.userCacheFlags).containsExactly(true, false);
            return null;
        }));
    }
    @Test void cachedParentsReceiveFreshProxiesForEveryTransaction() {
        run(f -> {
            Parent first = f.route.transaction(() -> ModelSelectWrapper.newInstance("local", Parent.class)
                    .cache().orderByAsc("id").selectList().getFirst()); // Leave its relation unloaded.
            Parent second = f.route.transaction(() -> {
                Parent row = ModelSelectWrapper.newInstance("local", Parent.class).cache().orderByAsc("id").selectList().getFirst();
                assertThat(row).isNotSameAs(first);
                assertThat(row.getChild().getName()).isEqualTo("user-1");
                return row;
            });
            assertThat(second.getChild().getName()).isEqualTo("user-1");
            assertThatThrownBy(first::getChild).hasMessageContaining("原始事务");
        });
    }
    @Test void cachedEntitiesAreIsolatedFromRollbackAndInvalidatedAfterSync() {
        run(f -> {
            assertThatThrownBy(() -> f.route.transaction(() -> {
                User row = ModelSelectWrapper.newInstance("local", User.class).cache().eq("id", "1").selectOne();
                f.sync(row).setName("rolled-back");
                throw new IllegalStateException("rollback");
            })).hasMessage("rollback");
            f.route.transaction(() -> {
                User row = ModelSelectWrapper.newInstance("local", User.class).cache().eq("id", "1").selectOne();
                assertThat(row.getName()).isEqualTo("user-1"); f.sync(row).setName("committed"); return null;
            });
            User reloaded = ModelSelectWrapper.newInstance("local", User.class).cache().eq("id", "1").selectOne();
            assertThat(reloaded.getName()).isEqualTo("committed");
        });
    }
    @Test void springBeforeCommitFlushesOnBoundConnectionAndCanRollback() {
        new ApplicationContextRunner()
                .withUserConfiguration(SqliteIntegrationTest.TestInfrastructure.class,
                        SqliteIntegrationTest.BootConfiguration.class, RecordingConfiguration.class, SpringConfiguration.class)
                .withPropertyValues("yulinlin.sqlite.file=" + directory.resolve("spring.db"),
                        "yulinlin.sqlite.group=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var sql = new JdbcTemplate(context.getBean(SqliteDatabase.class).dataSource()); schema(sql); seed(sql);
                    var service = context.getBean(SpringService.class);
                    service.rename(false);
                    assertThat(sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("spring");
                    assertThatThrownBy(() -> service.rename(true)).hasMessageContaining("business failure");
                    assertThat(sql.queryForObject("select name from proxy_user where id='1'", String.class)).isEqualTo("spring");
                    assertThatThrownBy(service::readOnly).hasMessageContaining("只读事务");
                    assertThat(SessionUtil.route().isOpenTransaction()).isFalse();
                    // Spring outer, route inner is supported too.
                    new TransactionTemplate(context.getBean(DataSourceTransactionManager.class)).execute(status -> {
                        SessionUtil.route().transaction(() -> {
                            User user = ModelSelectWrapper.newInstance("local", User.class).eq("id", "2").selectOne();
                            User proxy = SessionUtil.callable("local", () -> SessionUtil.route().getSyncProxy(user));
                            proxy.setName("rollback-template"); return null;
                        });
                        status.setRollbackOnly(); return null;
                    });
                    assertThat(sql.queryForObject("select name from proxy_user where id='2'", String.class)).isEqualTo("user-2");
                });
    }

    private static Parent parent(String id, String childId) { Parent p = new Parent(); p.setId(id); p.setChildId(childId); return p; }
    private static ChunkRelations chunk(String... ids) { ChunkRelations p = new ChunkRelations(); p.setIds(List.of(ids)); return p; }
    @Getter @Setter @JoinTable("proxy_parent")
    public static class Parent extends IdEntity<Parent> {
        private String childId;
        @JoinField(exist = false) @JoinLazy @JoinQuery(value = "${childId}") private User child;
        public String getComputedNull() { return null; }
    }
    @Getter @Setter @JoinTable("proxy_user")
    public static class User extends IdEntity<User> {
        private String name;
        private int age;
        private boolean enabled;
        private Integer status = 7;
        private List<String> tags = new ArrayList<>();
        @JoinField(exist = false) private int setterCalls;
        public void setName(String name) { setterCalls++; this.name = name; }
        public void setUp() { }
        @Override public boolean equals(Object other) { return other instanceof User u && Objects.equals(getId(), u.getId()) && Objects.equals(name, u.name); }
        @Override public int hashCode() { return Objects.hash(getId(), name); }
    }
    @Getter @Setter public static class ChunkRelations {
        private List<String> ids;
        @JoinLazy @JoinQuery(value = "${ids}", batchSize = 2, order = @JoinOrder(name = "name", asc = false))
        private List<User> users;
    }
    @Getter @Setter public static class EagerRelations {
        private List<String> ids;
        @JoinQuery(value = "${ids}", batchSize = 2, order = @JoinOrder(name = "name", asc = true)) private List<User> users;
    }
    @Getter @Setter public static class Chain {
        private List<String> ids;
        @JoinLazy @JoinQuery(value = "${ids}") private List<User> users;
        @JoinLazy @JoinQuery(value = "${users.id}") private List<User> copies;
    }
    @Getter @Setter public static class Circular {
        @JoinLazy @JoinQuery(value = "${child.id}") private Circular child;
    }
    @Getter @Setter @JoinTable("proxy_user") public static class NoKey { private String name; }
    @Getter @Setter @JoinTable("proxy_hook") public static class HookUser extends IdEntity<HookUser> implements BaseModel {
        private String name;
        private Integer updated;
        @Override public void updateBefore() { updated = 1; }
    }
    @Getter @Setter @JoinTable("proxy_version") public static class VersionUser extends IdEntity<VersionUser> {
        private String name;
        @JoinField(version = true) private Integer version;
    }
    public static class Recorder implements IRequestFilter {
        final List<Boolean> userCacheFlags = new ArrayList<>();
        final List<Set<String>> updatedFields = new ArrayList<>();
        boolean failNextUserQuery;
        int userQueries() { return userCacheFlags.size(); }
        @Override public QueryRequest selectBefore(String session, QueryRequest<?> request) {
            if (request.getEntityClass() == User.class) {
                userCacheFlags.add(request.isCache());
                if (failNextUserQuery) { failNextUserQuery = false; throw new IllegalStateException("query failure"); }
            }
            return request;
        }
        @Override public ExecuteRequest updateBefore(String session, ExecuteRequest<?> request) {
            for (var node : request.getWrappers()) if (node instanceof UpdateWrapper<?> update) {
                Set<String> keys = new HashSet<>();
                for (var value : ((AbstractFieldsWrapper<?, ?>) update.fields()).getList()) keys.add(value.getKey());
                updatedFields.add(keys);
            }
            return request;
        }
    }
    @Configuration(proxyBeanMethods = false) static class RecordingConfiguration {
        @Bean Recorder recorder() { return new Recorder(); }
    }
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement static class SpringConfiguration {
        @Bean DataSourceTransactionManager transactionManager(SqliteDatabase db) { return new DataSourceTransactionManager(db.dataSource()); }
        @Bean SpringService springService() { return new SpringService(); }
    }
    public static class SpringService {
        @Transactional @JoinSync public void rename(boolean fail) {
            User user = ModelSelectWrapper.newInstance("local", User.class).eq("id", "1").selectOne();
            user.setName(fail ? "must-rollback" : "spring");
            if (fail) throw new IllegalStateException("business failure");
        }
        @Transactional(readOnly = true) @JoinSync public void readOnly() {
            User user = ModelSelectWrapper.newInstance("local", User.class).eq("id", "1").selectOne();
            user.setName("forbidden");
        }
    }
}
