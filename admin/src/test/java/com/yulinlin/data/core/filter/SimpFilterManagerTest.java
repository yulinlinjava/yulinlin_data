package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.lang.util.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class SimpFilterManagerTest {
    private QueryRequest<Map> request() {
        return QueryRequest.newInstance("select 1", Map.of(), Map.class);
    }

    private IFilter filter(int order, String name, List<String> events) {
        return new IFilter() {
            @Override public int order() { return order; }
            @Override public BaseRequest before(String group, BaseRequest request) {
                events.add(name); return request;
            }
        };
    }

    @Test void sortingDoesNotOverflowAndEqualOrdersKeepRegistrationOrder() {
        List<String> events = new ArrayList<>();
        SimpFilterManager manager = new SimpFilterManager(List.of(
                filter(Integer.MAX_VALUE, "last", events),
                filter(0, "same-a", events), filter(0, "same-b", events),
                filter(Integer.MIN_VALUE, "first", events)));
        manager.before("mysql", request());
        assertThat(events).containsExactly("first", "same-a", "same-b", "last");
    }

    @Test void registrationDuringExecutionPublishesANewSnapshotForLaterCalls() {
        List<String> events = new ArrayList<>();
        SimpFilterManager manager = new SimpFilterManager();
        AtomicBoolean registered = new AtomicBoolean();
        manager.register(new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                events.add("first");
                if (registered.compareAndSet(false, true)) manager.register(filter(0, "new", events));
                return request;
            }
        });
        manager.register(filter(0, "second", events));
        manager.before("mysql", request());
        assertThat(events).containsExactly("first", "second");
        events.clear();
        manager.before("mysql", request());
        assertThat(events).containsExactly("first", "second", "new");
    }

    @Test void replacementFlowsToLaterFiltersAndNullRequestsFailClearly() {
        QueryRequest<Map> replacement = request();
        IFilter replace = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) { return replacement; }
        };
        IFilter verify = new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) {
                assertThat(request).isSameAs(replacement); return request;
            }
        };
        assertThat(new SimpFilterManager(List.of(replace, verify)).before("mysql", request())).isSameAs(replacement);
        SimpFilterManager invalid = new SimpFilterManager(List.of(new IFilter() {
            @Override public BaseRequest before(String group, BaseRequest request) { return null; }
        }));
        assertThatThrownBy(() -> invalid.before("mysql", request())).hasMessageContaining("不能返回 null");
        assertThatThrownBy(() -> invalid.register((IFilter) null)).isInstanceOf(NullPointerException.class);
    }

    @Test void afterReturnsTheLastReplacementAndRequestFilterLeavesCountsAlone() {
        IFilter first = new IFilter() {
            @Override public Object after(String group, BaseRequest request, Object data) { return "first"; }
        };
        IFilter second = new IFilter() {
            @Override public Object after(String group, BaseRequest request, Object data) { return data + ":second"; }
        };
        assertThat(new SimpFilterManager(List.of(first, second)).after("mysql", request(), List.of()))
                .isEqualTo("first:second");
        IRequestFilter listsOnly = new IRequestFilter() {
            @Override public Object selectAfter(String group, BaseRequest<?> request, List<Object> rows) {
                throw new AssertionError("count 不应触发列表过滤器");
            }
        };
        assertThat(listsOnly.after("mysql", request(), 5)).isEqualTo(5);
        assertThat(listsOnly.after("mysql", request(), null)).isNull();
    }

    @Test void unchangedPageListKeepsThePageInstance() {
        IRequestFilter filter = new IRequestFilter() { };
        Page<String> page = Page.of(List.of("value"), 100);
        assertThat(filter.after("mysql", request(), page)).isSameAs(page);
    }
}
