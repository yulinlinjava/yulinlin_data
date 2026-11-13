package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.anno.MetricsEnum;
import com.yulinlin.data.core.aop.AbstractAnnotationBeanPostProcessor;
import com.yulinlin.data.core.filter.IQueryFilter;
import com.yulinlin.data.core.filter.IWriteFilter;
import com.yulinlin.data.core.filter.IFilter;
import com.yulinlin.data.core.filter.IFilterManager;
import com.yulinlin.data.core.filter.SimpFilterManager;
import com.yulinlin.data.core.node.metrics.*;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.wrapper.impl.MetricsWrapper;
import com.yulinlin.data.lang.aop.BaseAnnotationBeanPostProcessor;
import com.yulinlin.data.lang.event.EventPublishManager;
import com.yulinlin.data.lang.event.IEvent;
import com.yulinlin.data.lang.event.IEventHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CoreApiCleanupTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "com.yulinlin.data.core.wrapper.IConditionObjectWrapper",
            "com.yulinlin.data.core.session.BufferToBean",
            "com.yulinlin.data.core.filter.IRequestFilter",
            "com.yulinlin.data.core.filter.IEntityFilter"
    })
    void removedLegacyTypesAreNotPackaged(String name) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(name));
    }

    @Test
    void metricsNoLongerExposeNonfunctionalObjectMethods() {
        for (Class<?> type : new Class<?>[] {IMetricsWrapper.class, MetricsWrapper.class}) {
            assertFalse(Arrays.stream(type.getMethods()).anyMatch(method -> method.getName().equals("object")));
        }
    }

    @ParameterizedTest
    @EnumSource(MetricsEnum.class)
    void everyWorkingMetricStillBuildsTheExpectedNode(MetricsEnum metric) {
        var wrapper = new MetricsWrapper<>();
        assertSame(wrapper, wrapper.field(metric, "amount", "result"));
        assertEquals(1, wrapper.getList().size());
        var field = wrapper.getList().iterator().next();
        assertEquals("amount", field.getFunction().getKey());
        assertEquals("result", field.getAlias());
        Class<?> expected = switch (metric) {
            case count -> CountMetrics.class;
            case distinctCount -> DistinctCountMetrics.class;
            case sum -> SumMetrics.class;
            case avg -> AvgMetrics.class;
            case min -> MinMetrics.class;
            case max -> MaxMetrics.class;
        };
        assertEquals(expected, field.getFunction().getClass());
    }

    @Test
    void queryFilterCanMatchEntityAndSubtypesExplicitly() {
        var calls = new AtomicInteger();
        class EntityQueryFilter implements IQueryFilter {
            @Override public boolean test(String group, BaseRequest request) {
                Class<?> type = request.getEntityClass();
                return type != null && Sample.class.isAssignableFrom(type);
            }
            @Override public void afterRow(String group, QueryRequest<?> request, Object row) { calls.incrementAndGet(); }
        }
        var filter = new EntityQueryFilter();
        assertTrue(filter.test("test", request(Sample.class)));
        assertTrue(filter.test("test", request(SampleChild.class)));
        assertFalse(filter.test("test", request(String.class)));
        var request = request(Sample.class);
        var manager = new SimpFilterManager(List.of(filter));
        assertSame(request, manager.beforeQuery("test", request));
        manager.checkSelectEach("test", request);
        manager.afterRow("test", request, new Sample());
        manager.afterRow("test", request(SampleChild.class), new SampleChild());
        manager.afterRow("test", request(String.class), "unmatched");
        assertEquals(2, calls.get());
    }

    @Test
    void writeFilterCanMatchEntityAndSubtypesExplicitly() {
        var affected = new AtomicInteger();
        class EntityWriteFilter implements IWriteFilter {
            @Override public boolean test(String group, BaseRequest request) {
                Class<?> type = request.getEntityClass();
                return type != null && Sample.class.isAssignableFrom(type);
            }
            @Override public void afterWrite(String group, ExecuteRequest<?> request, int count) { affected.addAndGet(count); }
        }
        var manager = new SimpFilterManager(List.of(new EntityWriteFilter()));
        manager.afterWrite("test", ExecuteRequest.ofInsert(Sample.class), 1);
        manager.afterWrite("test", ExecuteRequest.ofInsert(SampleChild.class), 2);
        manager.afterWrite("test", ExecuteRequest.ofInsert(String.class), 4);
        manager.checkSelectEach("test", request(Sample.class));
        manager.afterRow("test", request(Sample.class), new Sample());
        assertEquals(3, affected.get());
    }

    @Test
    void filtersNoLongerExposeMixedRequestOrWholeResultHooks() throws Exception {
        for (Class<?> type : List.of(IFilter.class, IQueryFilter.class, IWriteFilter.class,
                IFilterManager.class, SimpFilterManager.class)) {
            assertFalse(Arrays.stream(type.getMethods()).anyMatch(method ->
                    List.of("before", "after", "afterQuery").contains(method.getName())), type.getSimpleName());
        }
        assertEquals(List.of("order", "test"), Arrays.stream(IFilter.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).sorted().toList());
        assertEquals(void.class, IFilterManager.class.getMethod("afterWrite", String.class, ExecuteRequest.class, int.class).getReturnType());
        assertEquals(IQueryFilter.class, IQueryFilter.class.getMethod("supportsSelectEach").getDeclaringClass());
    }

    @Test
    void eventInterfaceStillProvidesWorkingDefaultPublication() {
        var deliveries = new AtomicInteger();
        EventPublishManager.instance().register(new IEventHandler<ProbeEvent>() {
            @Override public void handle(ProbeEvent event) { event.deliveries().incrementAndGet(); }
        });
        new ProbeEvent(deliveries).publish();
        assertEquals(1, deliveries.get());
    }

    @Test
    void emptyCompatibilityFacadeStillInheritsItsImplementation() {
        assertEquals(BaseAnnotationBeanPostProcessor.class, AbstractAnnotationBeanPostProcessor.class.getSuperclass());
    }

    private static <T> QueryRequest<T> request(Class<T> type) {
        return QueryRequest.newInstance("select 1", Map.of(), type);
    }

    static class Sample { }
    static class SampleChild extends Sample { }
    record ProbeEvent(AtomicInteger deliveries) implements IEvent { }
}
