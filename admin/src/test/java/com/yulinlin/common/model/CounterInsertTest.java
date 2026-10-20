package com.yulinlin.common.model;

import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.coder.MapCoderManager;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.IInsertWrapper;
import com.yulinlin.data.core.wrapper.IWrapperFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 放在 admin 中；不启动 Spring，也不连接数据库。 */
class CounterInsertTest {
    @JoinTable("sta_metrics")
    public static class Metrics extends SuperEntity<Metrics> {
        public String name;
        public String channel;
        public LocalDateTime time;
        public Boolean enabled;
        public AtomicBoolean active;
        public Byte small;
        public Short shortValue;
        public int count;
        public Long total;
        public Float floating;
        public Double decimal;
        public BigInteger bigInteger;
        public BigDecimal money;
        public AtomicInteger atomicInteger;
        public AtomicLong atomicLong;
        public LongAdder longAdder;
        public DoubleAdder metrics;
        @JoinField(exist = false) public String ignored;
    }

    @Test
    void supportsAllNumbersAndPreservesTypes() {
        Metrics one = numbers();
        CounterInsert.Snapshot merged = snapshot(one).merge(snapshot(numbers()));
        Metrics result = (Metrics) merged.entity();
        assertEquals((byte) 2, result.small.byteValue());
        assertEquals((short) 4, result.shortValue.shortValue());
        assertEquals(6, result.count);
        assertEquals(8L, result.total.longValue());
        assertEquals(2.5f, result.floating.floatValue());
        assertEquals(5d, result.decimal.doubleValue());
        assertEquals(new BigInteger("200000000000000000000"), result.bigInteger);
        assertEquals(new BigDecimal("0.20"), result.money);
        assertEquals(10, result.atomicInteger.get());
        assertEquals(12, result.atomicLong.get());
        assertEquals(14, result.longAdder.sum());
        assertEquals(1.5, result.metrics.sum());
        assertNotSame(one.atomicLong, result.atomicLong);
        assertNotSame(one.metrics, result.metrics);
        assertNull(result.getId(), "快照生成实体不提前触发 insertBefore");
    }

    @Test
    void timeBooleanAndAllOtherNonNumbersAreDimensionsAndNullIsIgnored() {
        Metrics first = numbers();
        first.time = LocalDateTime.of(2026, 10, 20, 12, 1, 2, 123);
        first.enabled = false;
        first.active = new AtomicBoolean(true);
        first.ignored = "不参与";
        CounterInsert.Snapshot captured = snapshot(first);
        Metrics same = numbers();
        same.time = first.time;
        same.enabled = false;
        same.active = new AtomicBoolean(true);
        same.ignored = "另一值";
        assertEquals(captured.key(), snapshot(same).key());
        assertEquals(first.time.toString(), captured.key().dimensions().get("time"));
        assertFalse(captured.key().dimensions().containsKey("channel"));
        assertFalse(captured.key().dimensions().containsKey("ignored"));
        same.channel = "A";
        assertNotEquals(captured.key(), snapshot(same).key());
        same.channel = null;
        same.time = first.time.plusNanos(1);
        assertNotEquals(captured.key(), snapshot(same).key());
        first.name = "修改";
        first.active.set(false);
        first.metrics.add(99);
        Metrics restored = (Metrics) captured.entity();
        assertEquals("播放量", restored.name);
        assertTrue(restored.active.get());
        assertEquals(0.75, restored.metrics.sum());
        assertNotEquals(captured.key(), CounterInsert.snapshot("h2", restored).key());
    }

    @Test
    void nullNumericValuesDoNotSplitGroupsAndZeroIsStillAValue() {
        Metrics first = new Metrics();
        first.name = "播放量";
        Metrics second = new Metrics();
        second.name = "播放量";
        second.total = 0L;
        CounterInsert.Snapshot a = snapshot(first);
        CounterInsert.Snapshot b = snapshot(second);
        assertEquals(a.key(), b.key());
        assertEquals(0L, ((Metrics) a.merge(b).entity()).total.longValue());
    }

    @Test
    void rejectsOverflowWithoutChangingOriginalSnapshot() {
        Metrics first = new Metrics();
        first.small = Byte.MAX_VALUE;
        first.total = 10L;
        Metrics second = new Metrics();
        second.small = 1;
        second.total = 20L;
        CounterInsert.Snapshot saved = snapshot(first);
        assertThrows(IllegalArgumentException.class, () -> saved.merge(snapshot(second)));
        assertEquals(10L, ((Metrics) saved.entity()).total.longValue());
        assertThrows(IllegalArgumentException.class, () ->
                CounterInsert.Numeric.of(new AtomicLong(Long.MAX_VALUE))
                        .add(CounterInsert.Numeric.of(new AtomicLong(1)), "atomicLong"));
        assertThrows(IllegalArgumentException.class, () ->
                CounterInsert.Numeric.of(Double.MAX_VALUE)
                        .add(CounterInsert.Numeric.of(Double.MAX_VALUE), "decimal"));
        assertThrows(IllegalArgumentException.class, () -> CounterInsert.Numeric.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> CounterInsert.Numeric.of(Float.POSITIVE_INFINITY));
    }

    @JoinTable("unsupported")
    public static class Unsupported {
        public AtomicReference<Integer> ref = new AtomicReference<>(1);
    }

    @Test
    void rejectsUnsupportedAtomicContainers() {
        assertThrows(IllegalArgumentException.class, () -> CounterInsert.snapshot("mysql", new Unsupported()));
    }

    @Test
    void schemaMapsAtomicFieldsToScalarTypes() {
        assertEquals(Integer.class, com.yulinlin.jdbc.schema.JdbcScalarTypes.storageType(AtomicInteger.class));
        assertEquals(Long.class, com.yulinlin.jdbc.schema.JdbcScalarTypes.storageType(AtomicLong.class));
        assertEquals(Long.class, com.yulinlin.jdbc.schema.JdbcScalarTypes.storageType(LongAdder.class));
        assertEquals(Double.class, com.yulinlin.jdbc.schema.JdbcScalarTypes.storageType(DoubleAdder.class));
        assertEquals(Boolean.class, com.yulinlin.jdbc.schema.JdbcScalarTypes.storageType(AtomicBoolean.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void scalarCodersWriteAndReadAtomicAndSmallNumbers() throws Exception {
        MapCoderManager manager = new MapCoderManager();
        var encoder = manager.createEncoderBuffer();
        var decoder = manager.createDecoderBuffer();
        assertEquals(7, encoder.encode(new AtomicInteger(7)));
        assertEquals(9L, encoder.encode(new AtomicLong(9)));
        assertEquals(false, encoder.encode(new AtomicBoolean(false)));
        assertEquals((byte) 1, encoder.encode((byte) 1));
        assertEquals((short) 2, encoder.encode((short) 2));
        AtomicLong value = (AtomicLong) decoder.decode("9007199254740993",
                Metrics.class.getDeclaredField("atomicLong"));
        assertEquals(9007199254740993L, value.get());
        AtomicBoolean active = (AtomicBoolean) decoder.decode("FALSE", Metrics.class.getDeclaredField("active"));
        assertFalse(active.get());
        assertThrows(ArithmeticException.class, () ->
                decoder.decode("2147483648", Metrics.class.getDeclaredField("atomicInteger")));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void submitAggregatesConcurrentlyAndFailedInsertReusesRequest() throws Exception {
        RouteSession previous = SessionUtil.route();
        RouteSession route = mock(RouteSession.class);
        IWrapperFactory factory = mock(IWrapperFactory.class);
        List<Metrics> created = new ArrayList<>();
        when(route.resolveWriteGroup(eq(Metrics.class), nullable(String.class))).thenReturn("mysql");
        when(route.getWrapperFactory()).thenReturn(factory);
        when(factory.createInsertWrapper(any(Object.class))).thenAnswer(call -> {
            Metrics entity = call.getArgument(0);
            entity.insertBefore();
            created.add(entity);
            return mock(IInsertWrapper.class);
        });
        List<ExecuteRequest<?>> attempted = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(true);
        when(route.insert(any(ExecuteRequest.class))).thenAnswer(call -> {
            ExecuteRequest<?> request = call.getArgument(0);
            attempted.add(request);
            if (fail.getAndSet(false)) throw new IllegalStateException("模拟写入失败");
            return request.getWrappers().size();
        });
        EntityCounterProperties properties = new EntityCounterProperties();
        properties.setFlushInterval(Duration.ofHours(1));
        properties.setFlushSize(10000);
        new SessionUtil(route);
        try (EntityCounterManager manager = new EntityCounterManager(route, properties)) {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    futures.add(executor.submit(() -> {
                        Metrics entity = new Metrics();
                        entity.name = "播放量";
                        entity.metrics = new DoubleAdder();
                        entity.metrics.add(0.5);
                        EntityCounter.insert(entity).submit();
                        entity.metrics.add(100);
                    }));
                }
                for (var future : futures) future.get();
            }
            assertEquals(1, manager.pendingRows());
            assertTrue(created.isEmpty());
            assertThrows(IllegalStateException.class, manager::flush);
            assertEquals(1, manager.pendingRows());
            assertEquals(100d, created.getFirst().metrics.sum());
            assertNotNull(created.getFirst().getId());
            assertEquals(1, manager.flush());
            assertSame(attempted.get(0), attempted.get(1));
            assertEquals(1, created.size(), "重试不能再次生成主键和时间");
            assertEquals(0, manager.pendingRows());
            EntityCounter.insert(numbers()).execute();
            assertEquals(0, manager.pendingRows(), "立即插入不进入聚合区");
        } finally {
            new SessionUtil(previous);
        }
    }

    private static CounterInsert.Snapshot snapshot(Metrics value) {
        return CounterInsert.snapshot("mysql", value);
    }

    private static Metrics numbers() {
        Metrics value = new Metrics();
        value.name = "播放量";
        value.small = 1;
        value.shortValue = 2;
        value.count = 3;
        value.total = 4L;
        value.floating = 1.25f;
        value.decimal = 2.5;
        value.bigInteger = new BigInteger("100000000000000000000");
        value.money = new BigDecimal("0.10");
        value.atomicInteger = new AtomicInteger(5);
        value.atomicLong = new AtomicLong(6);
        value.longAdder = new LongAdder();
        value.longAdder.add(7);
        value.metrics = new DoubleAdder();
        value.metrics.add(0.75);
        return value;
    }
}
