package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.core.anno.MetricsEnum;
import com.yulinlin.data.core.aop.AbstractAnnotationBeanPostProcessor;
import com.yulinlin.data.core.filter.IEntityFilter;
import com.yulinlin.data.core.node.metrics.*;
import com.yulinlin.data.core.request.QueryRequest;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CoreApiCleanupTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "com.yulinlin.data.core.wrapper.IConditionObjectWrapper",
            "com.yulinlin.data.core.session.BufferToBean"
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
    void entityFilterRemainsAUsableBusinessExtension() {
        var filter = new IEntityFilter<Sample>() { };
        assertTrue(filter.test("test", request(Sample.class)));
        assertTrue(filter.test("test", request(SampleChild.class)));
        assertFalse(filter.test("test", request(String.class)));
        var request = request(Sample.class);
        assertSame(request, filter.before("test", request));
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
