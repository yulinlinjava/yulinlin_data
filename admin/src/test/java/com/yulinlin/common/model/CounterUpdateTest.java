package com.yulinlin.common.model;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.wrapper.IWrapperFactory;
import com.yulinlin.data.core.wrapper.impl.UpdateFieldsWrapper;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.sql.SqlParamsContext;
import com.yulinlin.jdbc.sql.SqlParseManager;
import com.yulinlin.jdbc.sql.parse.wrapper.UpdateFieldsParse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅放 admin；使用节点和模拟路由，不启动 Spring、不连接数据库。 */
class CounterUpdateTest {
    @JoinTable("counter_video")
    public static class Video {
        @JoinMeta(primaryKey = true)
        @JoinField(name = "video_id", update = false)
        public Long id;
        @JoinField(name = "view_count")
        public Long views;
        public BigDecimal heat;
        public AtomicLong atomic;
        public String title = "不应覆盖";
        public Boolean enabled = false;
        @JoinField(update = false) public int protectedValue = 99;
        @JoinField(exist = false) public int ignored = 99;
        public int zero;
        public Long getViews() { return views; }
        public BigDecimal getHeat() { return heat; }
    }

    @Test
    void snapshotExcludesPrimaryAndNonCountersAndCopiesAtomicValues() {
        Video model = video(2);
        model.heat = new BigDecimal("-0.25");
        model.atomic = new AtomicLong(7);
        var command = CounterUpdate.snapshot(null, model, true);
        assertEquals(1L, command.id());
        assertEquals("id", command.primaryKey());
        assertEquals(Map.of("views", new BigDecimal("2"),
                "heat", new BigDecimal("-0.25"), "atomic", new BigDecimal("7")), command.deltas());
        model.id = 2L;
        model.views = 500L;
        model.atomic.set(500);
        assertEquals(1L, command.id());
        assertEquals(new BigDecimal("7"), command.deltas().get("atomic"));
        assertThrows(UnsupportedOperationException.class,
                () -> command.deltas().put("views", BigDecimal.ZERO));
    }

    @Test
    void numbersKeepPrecisionAndRejectNonFiniteValues() {
        LongAdder integer = new LongAdder();
        integer.add(3);
        DoubleAdder decimal = new DoubleAdder();
        decimal.add(0.25);
        for (Number value : List.<Number>of((byte) 3, (short) 3, 3, 3L,
                new AtomicInteger(3), new AtomicLong(3), integer))
            assertEquals(new BigDecimal("3"), CounterNumbers.snapshot(value));
        for (Number value : List.<Number>of(0.25f, 0.25d, decimal))
            assertEquals(new BigDecimal("0.25"), CounterNumbers.snapshot(value));
        BigInteger huge = new BigInteger("100000000000000000000");
        assertEquals(new BigDecimal(huge), CounterNumbers.snapshot(huge));
        assertEquals(new BigDecimal("0.123456789123456789"),
                CounterNumbers.snapshot(new BigDecimal("0.123456789123456789")));
        assertThrows(IllegalArgumentException.class, () -> CounterNumbers.snapshot(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> CounterNumbers.snapshot(Float.POSITIVE_INFINITY));
        assertEquals(3L, CounterNumbers.parameter(new BigDecimal("3.00")));
        assertEquals(new BigDecimal(huge), CounterNumbers.parameter(new BigDecimal(huge)));
    }

    @JoinTable("missing") public static class MissingPrimary { public int count; }
    @JoinTable("duplicate") public static class DuplicatePrimary {
        @JoinMeta(primaryKey = true) public Long first = 1L;
        @JoinMeta(primaryKey = true) public Long second = 2L;
    }

    @Test
    void requiresExactlyOneNonEmptyPrimaryKey() {
        Video value = video(1);
        value.id = null;
        assertThrows(IllegalArgumentException.class, () -> CounterUpdate.snapshot(null, value, null));
        assertThrows(IllegalArgumentException.class, () -> CounterUpdate.freezeId("  "));
        assertThrows(IllegalArgumentException.class,
                () -> CounterUpdate.snapshot(null, new MissingPrimary(), null));
        assertThrows(IllegalArgumentException.class,
                () -> CounterUpdate.snapshot(null, new DuplicatePrimary(), null));
    }

    @Test
    void modelAndChainShareResolvedGroupAndRetryPreservesDeltas() {
        Harness harness = new Harness();
        try (EntityCounterManager manager = harness.manager()) {
            Video model = video(1);
            var builder = EntityCounter.update(model);
            model.views = 2L; // execute/submit 时才取快照
            builder.submit();
            model.views = 999L;
            assertThrows(IllegalStateException.class, builder::submit);
            EntityCounter.update("mysql", Video.class, 1L).inc(Video::getViews, 3).submit();
            assertEquals(1, manager.pendingRows());
            doThrow(new IllegalStateException("模拟失败")).when(harness.route).update(any());
            assertThrows(IllegalStateException.class, manager::flush);
            assertEquals(1, manager.pendingRows());
            EntityCounter.update(video(-1)).submit();
            harness.recordUpdates();
            assertEquals(1, manager.flush());
            var request = harness.requests.getFirst();
            assertEquals("mysql", request.getSession());
            assertEquals(Video.class, request.getFromClass());
            UpdateWrapper<?> wrapper = (UpdateWrapper<?>) request.getWrappers().getFirst();
            assertEquals(1, wrapper.fields().getList().size(), "不能生成默认字段覆盖赋值");
            assertEquals(4L, wrapper.fields().getList().iterator().next().getValue());
            var params = new SimpParamsContext(RequestType.update, Map.of(),
                    new JdbcCoderManager().createEncoderBuffer(), Video.class, true);
            var parsed = (com.yulinlin.data.core.parse.ParseResult) new SqlParseManager().parse(wrapper, params);
            var sql = (com.yulinlin.jdbc.session.SqlNode) parsed.getRequest();
            assertTrue(sql.getSql().contains("view_count = view_count + ?"));
            assertTrue(sql.getSql().contains("video_id"), "主键 update=false 不能阻止条件列映射");
            assertEquals(List.of(4L, 1L), sql.getList());
            verify(harness.route, never()).insert(any());
        }
    }

    @Test
    void cancellationAndZeroDoNotExecuteAndImmediateUpdateCanJoinTransaction() {
        Harness harness = new Harness();
        try (EntityCounterManager manager = harness.manager()) {
            assertEquals(0, EntityCounter.update(video(0)).execute());
            EntityCounter.update(video(5)).submit();
            EntityCounter.update(video(-5)).submit();
            assertEquals(0, manager.flush());
            assertTrue(harness.requests.isEmpty());
            when(harness.route.isOpenTransaction()).thenReturn(true);
            assertThrows(RuntimeException.class, () -> EntityCounter.update(video(1)).submit());
            assertEquals(1, EntityCounter.update(video(-2)).clearCache(true).execute());
            assertTrue(harness.requests.getFirst().getClearCache());
            var wrapper = (UpdateWrapper<?>) harness.requests.getFirst().getWrappers().getFirst();
            var operation = wrapper.fields().getList().iterator().next();
            assertEquals(com.yulinlin.data.core.node.atomic.AtomicValue.OperateEnum.dec, operation.getOperate());
            assertEquals(2L, operation.getValue());
        }
    }

    @Test
    void chainAcceptsSignedIncrementsAndCancelsAggregatedDeltas() {
        Harness harness = new Harness();
        try (EntityCounterManager manager = harness.manager()) {
            assertEquals(1, EntityCounter.update(Video.class, 1L).inc(Video::getViews, -5).execute());
            var wrapper = (UpdateWrapper<?>) harness.requests.getFirst().getWrappers().getFirst();
            var operation = wrapper.fields().getList().iterator().next();
            assertEquals(com.yulinlin.data.core.node.atomic.AtomicValue.OperateEnum.dec, operation.getOperate());
            assertEquals(5L, operation.getValue());

            harness.requests.clear();
            EntityCounter.update(Video.class, 1L).inc(Video::getViews, 5)
                    .inc(Video::getViews, -2).submit();
            EntityCounter.update(Video.class, 1L).inc(Video::getViews, -3).submit();
            assertEquals(0, manager.flush());
            assertTrue(harness.requests.isEmpty());
            assertEquals(0, EntityCounter.update(Video.class, 1L).inc(Video::getViews, 0).execute());

            // MIN_VALUE 不能先在 long 上取负，底层应通过 BigDecimal 生成正确的减量。
            assertEquals(1, EntityCounter.update(Video.class, 1L)
                    .inc(Video::getViews, Long.MIN_VALUE).execute());
            wrapper = (UpdateWrapper<?>) harness.requests.getFirst().getWrappers().getFirst();
            operation = wrapper.fields().getList().iterator().next();
            assertEquals(new BigDecimal("9223372036854775808"), operation.getValue());
            assertThrows(IllegalArgumentException.class, () ->
                    EntityCounter.update(Video.class, 1L).inc(Video::getViews, Double.NaN));
            harness.requests.clear();
            EntityCounter.update(Video.class, 1L).inc(Video::getViews, Long.MIN_VALUE)
                    .inc(Video::getViews, -1).execute();
            wrapper = (UpdateWrapper<?>) harness.requests.getFirst().getWrappers().getFirst();
            assertEquals(new BigDecimal("9223372036854775809"),
                    wrapper.fields().getList().iterator().next().getValue());
        }
    }

    @Test
    void incAndDecAcceptSignedDecimalsLargeIntegersAndAtomicSnapshots() {
        Harness harness = new Harness();
        try (EntityCounterManager manager = harness.manager()) {
            LongAdder integer = new LongAdder();
            integer.add(-7);
            DoubleAdder decimal = new DoubleAdder();
            decimal.add(-0.125);
            List<Number> numbers = List.of((byte) -2, (short) 3, -4, 5L, -0.5f, 0.25d,
                    new BigInteger("-100000000000000000000"),
                    new BigDecimal("0.123456789123456789"),
                    new AtomicInteger(-3), new AtomicLong(4), integer, decimal, Long.MIN_VALUE);
            for (Number number : numbers) {
                for (boolean subtract : List.of(false, true)) {
                    harness.requests.clear();
                    var counter = EntityCounter.update(Video.class, 1L);
                    if (subtract) counter.dec(Video::getHeat, number);
                    else counter.inc(Video::getHeat, number);
                    assertEquals(1, counter.execute());
                    BigDecimal expected = CounterNumbers.snapshot(number);
                    if (subtract) expected = expected.negate();
                    var wrapper = (UpdateWrapper<?>) harness.requests.getFirst().getWrappers().getFirst();
                    var operation = wrapper.fields().getList().iterator().next();
                    assertEquals(expected.signum() < 0
                                    ? com.yulinlin.data.core.node.atomic.AtomicValue.OperateEnum.dec
                                    : com.yulinlin.data.core.node.atomic.AtomicValue.OperateEnum.inc,
                            operation.getOperate());
                    assertEquals(0, expected.abs().compareTo(new BigDecimal(operation.getValue().toString())));
                }
            }
            harness.requests.clear();
            AtomicLong amount = new AtomicLong(2);
            var counter = EntityCounter.update(Video.class, 1L).inc(Video::getHeat, amount)
                    .dec(Video::getHeat, new BigDecimal("-0.25"));
            amount.set(999); // 链式调用时已读取快照
            counter.submit();
            EntityCounter.update(Video.class, 1L).inc(Video::getHeat, new BigDecimal("-2.25")).submit();
            assertEquals(0, manager.flush());
            assertTrue(harness.requests.isEmpty());
            assertEquals(0, EntityCounter.update(Video.class, 1L).inc(Video::getHeat, 0.1f)
                    .dec(Video::getHeat, new BigDecimal("0.1")).execute());
            assertEquals(0, EntityCounter.update(Video.class, 1L).dec(Video::getHeat, -0.0).execute());
            assertThrows(NullPointerException.class,
                    () -> EntityCounter.update(Video.class, 1L).inc(Video::getHeat, null));
            assertThrows(NullPointerException.class,
                    () -> EntityCounter.update(Video.class, 1L).dec(Video::getHeat, null));
            for (Number invalid : List.of(Double.NaN, Float.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                assertThrows(IllegalArgumentException.class,
                        () -> EntityCounter.update(Video.class, 1L).inc(Video::getHeat, invalid));
                assertThrows(IllegalArgumentException.class,
                        () -> EntityCounter.update(Video.class, 1L).dec(Video::getHeat, invalid));
            }
        }
    }

    @Test
    void jdbcArithmeticUsesNumericParameterWhileOrdinaryEncodingIsUnchanged() {
        var buffer = new JdbcCoderManager().createEncoderBuffer();
        var context = new SqlParamsContext(new SimpParamsContext(
                RequestType.update, Map.of(), buffer, Video.class, true), new SqlParseManager());
        UpdateFieldsWrapper<Video> fields = new UpdateFieldsWrapper<>();
        fields.inc("heat", new BigDecimal("0.25"));
        fields.dec("views", 2L);
        String sql = new UpdateFieldsParse().parse(fields, context, context.parseManager());
        assertTrue(sql.contains(" + "));
        assertTrue(sql.contains(" - "));
        assertTrue(buffer.toMap().containsValue(new BigDecimal("0.25")));
        assertTrue(buffer.toMap().containsValue(2L));
        assertEquals("0.25", buffer.encode(new BigDecimal("0.25")));
    }

    private static Video video(long increment) {
        Video value = new Video();
        value.id = 1L;
        value.views = increment;
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class Harness {
        final RouteSession route = mock(RouteSession.class);
        final List<ExecuteRequest<?>> requests = new ArrayList<>();
        Harness() {
            IWrapperFactory factory = mock(IWrapperFactory.class);
            when(route.getWrapperFactory()).thenReturn(factory);
            doAnswer(call -> new UpdateWrapper<>()).when(factory).createUpdateWrapper();
            when(route.resolveWriteGroup(eq(Video.class), nullable(String.class))).thenReturn("mysql");
            recordUpdates();
        }
        void recordUpdates() {
            doAnswer(call -> {
                ExecuteRequest<?> request = call.getArgument(0);
                requests.add(request);
                return request.getWrappers().size();
            }).when(route).update(any(ExecuteRequest.class));
        }
        EntityCounterManager manager() {
            EntityCounterProperties properties = new EntityCounterProperties();
            properties.setFlushInterval(Duration.ofHours(1));
            properties.setFlushSize(10000);
            return new EntityCounterManager(route, properties);
        }
    }
}
