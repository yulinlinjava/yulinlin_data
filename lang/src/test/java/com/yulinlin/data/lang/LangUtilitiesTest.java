package com.yulinlin.data.lang;

import com.yulinlin.data.lang.cache.ExpiryMap;
import com.yulinlin.data.lang.cache.ResourcePool;
import com.yulinlin.data.lang.event.AbstractEventPublishManager;
import com.yulinlin.data.lang.event.IEventHandler;
import com.yulinlin.data.lang.fixture.scan.ScanAbstract;
import com.yulinlin.data.lang.fixture.scan.ScanBase;
import com.yulinlin.data.lang.fixture.scan.ScanConcrete;
import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;
import com.yulinlin.data.lang.lambda.LambdaUtils;
import com.yulinlin.data.lang.lazy.SmartCache;
import com.yulinlin.data.lang.reflection.AnnotationUtil;
import com.yulinlin.data.lang.util.ClassUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.Page;
import com.yulinlin.data.lang.util.StringUtil;
import com.yulinlin.data.lang.util.ScanUtils;
import com.yulinlin.data.lang.util.ThreadUtil;
import com.yulinlin.data.lang.util.WeightedSelector;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LangUtilitiesTest {

    @Test
    void stringOperationsKeepTheirPublicResults() {
        assertEquals("abc", StringUtil.removeLine(" a\tb\r\nc\f"));
        assertEquals("user_name", StringUtil.javaToColumn("userName"));
        assertEquals("userName", StringUtil.columnToJava("user_name"));
        assertEquals("UserName", StringUtil.tableToClass("user_name"));
        assertTrue(StringUtil.isLowerCaseFirstOne('a'));
        assertFalse(StringUtil.isLowerCaseFirstOne('A'));
    }

    @Test
    void serializedLambdaDoesNotReuseCapturedArgumentsFromAnotherInstance() {
        var first = LambdaUtils.serializedLambda(capturing("first-"));
        var second = LambdaUtils.serializedLambda(capturing("second-"));

        assertEquals("first-", first.getCapturedArg(0));
        assertEquals("second-", second.getCapturedArg(0));
    }

    @Test
    void resourcePoolReturnsPermitWhenFactoryFails() {
        AtomicInteger calls = new AtomicInteger();
        ResourcePool<String> pool = new ResourcePool<>(1, () -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("first failure");
            return "resource";
        });

        assertThrows(IllegalStateException.class, pool::acquire);
        String resource = assertTimeoutPreemptively(Duration.ofSeconds(1), pool::acquire);
        assertEquals("resource", resource);
        pool.release(resource);
        pool.close();
    }

    @Test
    void expiringMapLoadsOnceAndExpiresOnRead() throws InterruptedException {
        ExpiryMap<String, String> map = new ExpiryMap<>(20, TimeUnit.MILLISECONDS, 10, null);
        AtomicInteger loads = new AtomicInteger();
        assertEquals("v1", map.get("key", key -> "v" + loads.incrementAndGet()));
        assertEquals("v1", map.get("key", key -> "v" + loads.incrementAndGet()));
        Thread.sleep(40);
        assertNull(map.get("key"));
        map.shutdown();
    }

    @Test
    void smartCacheValidatesTtlAndCanBeClosed() {
        assertThrows(IllegalArgumentException.class, () -> SmartCache.of(() -> "value", 0));
        AtomicInteger loads = new AtomicInteger();
        SmartCache<Integer> cache = SmartCache.of(loads::incrementAndGet, 10);
        assertEquals(1, cache.get());
        assertEquals(1, cache.get());
        cache.refresh();
        assertEquals(2, cache.get());
        cache.close();
        assertThrows(IllegalStateException.class, cache::get);
    }

    @Test
    void eventManagerIgnoresUnregisteredAsyncEventsAndPublishesRegisteredOnes() {
        TestEventManager manager = new TestEventManager();
        assertDoesNotThrow(() -> manager.asyncPublish("unregistered"));
        AtomicInteger count = new AtomicInteger();
        manager.register(new IEventHandler<TestEvent>() {
            @Override public void handle(TestEvent event) { count.incrementAndGet(); }
            @Override public Class<?> getEventClass() { return TestEvent.class; }
        });
        manager.publish(new TestEvent());
        assertEquals(1, count.get());
    }

    @Test
    void parameterAnnotationAndPrimitiveWrappersAreResolved() throws Exception {
        Method method = LangUtilitiesTest.class.getDeclaredMethod("annotated", String.class);
        assertNotNull(AnnotationUtil.findAnnotation(method, Marker.class, 0));
        assertTrue(ClassUtil.isPrimitive(boolean.class));
        assertTrue(ClassUtil.isPrimitive(Boolean.class));
        assertTrue(ClassUtil.isPrimitive(Short.class));
    }

    @Test
    void weightedSelectorUsesAnImmutableReadSnapshot() {
        WeightedSelector<String> selector = new WeightedSelector<>();
        assertThrows(IllegalStateException.class, selector::select);
        selector.add("only", 1);
        assertEquals("only", selector.select());
        assertThrows(UnsupportedOperationException.class, () -> selector.list().clear());
    }

    @Test
    void dateBoundariesResetSubSecondPrecision() {
        DateTime value = DateTime.date(LocalDateTime.of(2026, 1, 2, 12, 30, 40, 123_456_789));
        assertEquals(LocalDateTime.of(2026, 1, 2, 0, 0), value.beginOfDay().toLocalDateTime());
        assertEquals(LocalDateTime.of(2026, 1, 2, 23, 59, 59, 999_999_999),
                value.endOfDay().toLocalDateTime());
    }

    @Test
    void subclassScanHonorsMustConcreteFlag() {
        String packageName = "com.yulinlin.data.lang.fixture.scan";
        assertEquals(java.util.Set.of(ScanConcrete.class),
                ScanUtils.scanSubclass(packageName, ScanBase.class, true));
        assertEquals(java.util.Set.of(ScanConcrete.class, ScanAbstract.class),
                ScanUtils.scanSubclass(packageName, ScanBase.class, false));
    }

    @Test
    void inMemoryPageValidatesBoundsAndReturnsRequestedWindowSize() {
        assertThrows(IllegalArgumentException.class, () -> Page.page(List.of(1), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> Page.page(List.of(1), 1, 0));
        assertEquals(List.of(), Page.page(List.of(1), 2, 1));

        Page<Integer> values = Page.of(List.of(1, 2));
        assertEquals(1, values.random(1).getList().size());
        assertThrows(IllegalArgumentException.class, () -> values.random(-1));
    }

    @Test
    void explicitVirtualSubmissionUsesAJdkVirtualThread() throws Exception {
        assertTrue(ThreadUtil.submitVirtual(() -> Thread.currentThread().isVirtual()).get(1, TimeUnit.SECONDS));
        assertTrue(ThreadUtil.submit(() -> Thread.currentThread().isVirtual()).get(1, TimeUnit.SECONDS));
    }

    private static LambdaPropertyFunction<Bean> capturing(String prefix) {
        return bean -> prefix + bean.name;
    }

    @SuppressWarnings("unused")
    private static void annotated(@Marker String value) {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface Marker {
    }

    private static final class Bean {
        private String name;
    }

    private static final class TestEvent {
    }

    private static final class TestEventManager extends AbstractEventPublishManager<TestEventManager> {
    }
}
