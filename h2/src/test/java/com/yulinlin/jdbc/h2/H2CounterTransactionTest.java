package com.yulinlin.jdbc.h2;

import com.yulinlin.common.model.EntityCounter;
import com.yulinlin.common.model.EntityCounterManager;
import com.yulinlin.common.model.EntityCounterProperties;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.exception.OrmException;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.factory.WrapperFactoryImpl;
import com.yulinlin.data.core.wrapper.impl.*;
import com.yulinlin.jdbc.h2.coder.H2CoderManager;
import lombok.Data;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("SessionUtil.route")
@ResourceLock("EntityCounterManager.current")
class H2CounterTransactionTest {
    @ParameterizedTest @ValueSource(ints = {1, 4})
    void failedFlushRollsBackTheGroupAndRetryDoesNotRepeatSuccessfulIncrements(int parallelConnections) throws Exception {
        try (var f = new Fixture(parallelConnections)) {
            EntityCounter.update(Row.class, "a").inc(Row::getTotal, 1).submit();
            EntityCounter.update(Row.class, "b").inc(Row::getTotal, 10).submit();
            assertThrows(OrmException.class, f.manager::flush);
            assertEquals(0, f.total("a"), "failed group must not leave a committed increment");
            assertEquals(0, f.total("b"));
            assertEquals(2, f.manager.pendingRows());
            assertFalse(f.route.isOpenTransaction());
            assertFalse(f.session.isOpenTransaction());

            // 修复业务增量：重试区中的 b +10 与新提交的 -9 合并为 +1。
            EntityCounter.update(Row.class, "b").dec(Row::getTotal, 9).submit();
            assertEquals(2, f.manager.flush());
            assertEquals(1, f.total("a"), "successful retry must apply a exactly once");
            assertEquals(1, f.total("b"));
            assertEquals(0, f.manager.pendingRows());
        }
    }

    @Test void manualFlushInsideBusinessTransactionIsRejectedBeforeDrainingTheBuffer() throws Exception {
        try (var f = new Fixture(4)) {
            EntityCounter.update(Row.class, "a").inc(Row::getTotal).submit();
            assertThrows(NoticeException.class, () -> f.route.transaction(() -> f.manager.flush()));
            assertEquals(1, f.manager.pendingRows());
            assertEquals(0, f.total("a"));
            assertEquals(1, f.manager.flush());
            assertEquals(1, f.total("a"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void immediateCounterUpdatesParticipateInNestedAndOuterTransactions(boolean rollbackOuter) throws Exception {
        try (var f = new Fixture(4)) {
            Runnable work = () -> f.route.transaction(() -> {
                EntityCounter.update(Row.class, "a").inc(Row::getTotal).execute();
                assertThrows(IllegalArgumentException.class, () -> f.route.transaction(() -> {
                    EntityCounter.update(Row.class, "a").inc(Row::getTotal, 2).execute();
                    throw new IllegalArgumentException("inner");
                }));
                EntityCounter.update(Row.class, "b").inc(Row::getTotal).execute();
                if (rollbackOuter) throw new IllegalStateException("outer");
                return null;
            });
            if (rollbackOuter) assertThrows(IllegalStateException.class, work::run);
            else work.run();
            assertEquals(rollbackOuter ? 0 : 1, f.total("a"));
            assertEquals(rollbackOuter ? 0 : 1, f.total("b"));
        }
    }

    @Test void bufferedCountersRejectTransactionalSubmissionAndStillAggregateOutsideIt() throws Exception {
        try (var f = new Fixture(4)) {
            f.route.transaction(() -> {
                assertThrows(NoticeException.class, () -> EntityCounter.update(Row.class, "a").inc(Row::getTotal).submit());
                assertThrows(NoticeException.class, () -> EntityCounter.insert(new Row("c", 1L)).submit());
                return null;
            });
            assertEquals(0, f.manager.pendingRows());
            for (int i = 0; i < 3; i++) EntityCounter.update(Row.class, "a").inc(Row::getTotal).submit();
            assertEquals(1, f.manager.pendingRows());
            assertEquals(0, f.total("a"));
            assertEquals(1, f.manager.flush());
            assertEquals(3, f.total("a"));
            EntityCounter.insert(new Row("c", 1L)).submit();
            EntityCounter.insert(new Row("c", 2L)).submit();
            assertEquals(1, f.manager.pendingRows());
            assertEquals(1, f.manager.flush());
            assertEquals(3, f.total("c"));
        }
    }

    @Data @JoinTable("counter_rows")
    public static class Row {
        @JoinMeta(primaryKey = true)
        private String id;
        private Long total;
        public Row() { }
        Row(String id, Long total) { this.id = id; this.total = total; }
    }

    private static final class Fixture implements AutoCloseable {
        final RouteSession previous = SessionUtil.route();
        final Connection anchor;
        final H2Session session;
        final RouteSession route;
        final EntityCounterManager manager;
        Fixture(int parallelConnections) throws Exception {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:counter_transactions_" + UUID.randomUUID());
            anchor = source.getConnection();
            try (var statement = anchor.createStatement()) {
                statement.execute("CREATE TABLE \"counter_rows\" (\"id\" VARCHAR(20) PRIMARY KEY, \"total\" BIGINT CHECK (\"total\" <= 5))");
                statement.execute("INSERT INTO \"counter_rows\" VALUES ('a', 0), ('b', 0)");
            }
            session = new H2Session(source);
            session.setGroup("h2");
            session.setCoderManager(new H2CoderManager());
            session.setParallelConnections(parallelConnections);
            route = RouteSession.builder().build();
            route.setLoadBalance(new RandomLoadBalance());
            route.registerSession(session);
            route.setWrapperFactory(WrapperFactoryImpl.newInstance(InsertWrapper.class, UpdateWrapper.class,
                    DeleteWrapper.class, SelectWrapper.class, GroupWrapper.class));
            new SessionUtil(route);
            var properties = new EntityCounterProperties();
            properties.setFlushInterval(Duration.ofDays(1));
            properties.setFlushSize(Integer.MAX_VALUE);
            manager = new EntityCounterManager(route, properties);
        }
        long total(String id) throws Exception {
            try (var statement = anchor.prepareStatement("SELECT \"total\" FROM \"counter_rows\" WHERE \"id\" = ?")) {
                statement.setString(1, id);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    return rows.getLong(1);
                }
            }
        }
        @Override public void close() throws Exception {
            try {
                if (route.isOpenTransaction()) route.rollbackTransaction();
                manager.close();
            } finally {
                new SessionUtil(previous);
                session.getThreadPoolExecutor().shutdownNow();
                anchor.close();
            }
        }
    }
}
