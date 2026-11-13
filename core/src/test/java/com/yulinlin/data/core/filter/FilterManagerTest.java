package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.event.IInitEvent;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FilterManagerTest {
    private QueryRequest<Row> query() { return QueryRequest.newInstance("select 1", Map.of(), Row.class); }
    private ExecuteRequest<Row> write() { return ExecuteRequest.ofInsert(Row.class); }

    @Test void springBeanDiscoveryIncludesBothKindsAndDefaultInitFilter() {
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var config = new com.yulinlin.data.core.YulinlinCoreAutoConfig();
        beans.registerSingleton("init", config.initFilter());
        beans.registerSingleton("query", new IQueryFilter() {
            @Override public void afterRow(String group, QueryRequest<?> request, Object row) { ((Row) row).value = "bean"; }
        });
        beans.registerSingleton("write", new IWriteFilter() { });
        var manager = config.filterManager(beans.getBeanProvider(IFilter.class));
        var row = new Row();
        manager.checkSelectEach("db", query()); manager.afterRow("db", query(), row);
        assertEquals(1, row.initCalls); assertEquals("bean", row.initializedValue);
    }

    @Test void queryAndWriteFiltersDoNotEvenMatchTheOtherRequestKind() {
        var calls = new ArrayList<String>();
        var manager = new SimpFilterManager(List.of(new IQueryFilter() {
            @Override public boolean test(String group, BaseRequest request) {
                assertInstanceOf(QueryRequest.class, request); return true;
            }
            @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) {
                calls.add("query.before"); return request;
            }
            @Override public void afterRow(String group, QueryRequest<?> request, Object row) { calls.add("query.row"); }
        }, new IWriteFilter() {
            @Override public boolean test(String group, BaseRequest request) {
                assertInstanceOf(ExecuteRequest.class, request); return true;
            }
            @Override public ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) {
                calls.add("write.before"); return request;
            }
            @Override public void afterWrite(String group, ExecuteRequest<?> request, int count) {
                assertEquals(3, count); calls.add("write.after");
            }
        }));
        var query = query(); var write = write(); var row = new Row();
        assertSame(query, manager.beforeQuery("db", query));
        manager.checkSelectEach("db", query);
        manager.afterRow("db", query, row);
        assertSame(write, manager.beforeWrite("db", write));
        manager.afterWrite("db", write, 3);
        assertEquals(List.of("query.before", "query.row", "write.before", "write.after"), calls);
    }

    @Test void oneFilterCanImplementBothKindsWithStableOrderAndMatching() {
        var calls = new ArrayList<String>();
        class Both implements IQueryFilter, IWriteFilter {
            @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) { calls.add("query"); return request; }
            @Override public ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) { calls.add("write"); return request; }
            @Override public void afterRow(String group, QueryRequest<?> request, Object row) { calls.add("both"); }
            @Override public int order() { return 2; }
        }
        var manager = new SimpFilterManager(List.of(new Both(), rowFilter(calls, "first", 1), rowFilter(calls, "second", 1),
                new IQueryFilter() {
                    @Override public boolean test(String group, BaseRequest request) { return false; }
                    @Override public void afterRow(String group, QueryRequest<?> request, Object row) { fail("unmatched"); }
                }));
        manager.beforeQuery("db", query()); manager.beforeWrite("db", write());
        manager.afterRow("db", query(), new Row());
        assertEquals(List.of("query", "write", "first", "second", "both"), calls);
    }

    @Test void rowHookInitializesEachObjectOnce() {
        var manager = new SimpFilterManager(List.of(new InitFilter()));
        var a = new Row(); var b = new Row();
        manager.afterRow("db", query(), a); manager.afterRow("db", query(), b);
        assertEquals(1, a.initCalls); assertEquals(1, b.initCalls);
    }

    @Test void rowEnhancementsRetainTheirOrderAroundInitialization() {
        var manager = new SimpFilterManager(List.of(new InitFilter(), new IQueryFilter() {
            @Override public void afterRow(String group, QueryRequest<?> request, Object row) { ((Row) row).value = "before-init"; }
        }, new IQueryFilter() {
            @Override public int order() { return 10000; }
            @Override public void afterRow(String group, QueryRequest<?> request, Object value) {
                Row row = (Row) value;
                assertEquals(1, row.initCalls); assertEquals("before-init", row.initializedValue);
                row.value = "after-init";
            }
        }));
        var row = new Row();
        manager.afterRow("db", query(), row);
        assertEquals("after-init", row.value);
        assertDoesNotThrow(() -> manager.checkSelectEach("db", query()));
    }

    @Test void writeHookReceivesActualAffectedRowsIncludingZero() {
        var seen = new ArrayList<Integer>();
        var manager = new SimpFilterManager(List.of(new IWriteFilter() {
            @Override public void afterWrite(String group, ExecuteRequest<?> request, int count) { seen.add(count); }
        }));
        manager.afterWrite("db", write(), 2);
        manager.afterWrite("db", write(), 0);
        assertEquals(List.of(2, 0), seen);
    }

    @Test void customManagerUsesOnlyTypedHooks() {
        var calls = new ArrayList<String>();
        IFilterManager manager = new IFilterManager() {
            @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) { calls.add("query.before"); return request; }
            @Override public ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) { calls.add("write.before"); return request; }
            @Override public void afterRow(String group, QueryRequest<?> request, Object row) { calls.add("row"); }
            @Override public void afterWrite(String group, ExecuteRequest<?> request, int count) { calls.add("write.after"); }
        };
        var query = query(); var write = write();
        assertSame(query, manager.beforeQuery("db", query));
        manager.afterRow("db", query, new Row());
        assertSame(write, manager.beforeWrite("db", write)); manager.afterWrite("db", write, 1);
        assertEquals(List.of("query.before", "row", "write.before", "write.after"), calls);
        assertDoesNotThrow(() -> manager.checkSelectEach("db", query));
    }

    @Test void nullRequestsFailImmediately() {
        var invalidQuery = new SimpFilterManager(List.of(new IQueryFilter() {
            @Override public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) { return null; }
        }));
        assertThrows(IllegalStateException.class, () -> invalidQuery.beforeQuery("db", query()));
        var invalidWrite = new SimpFilterManager(List.of(new IWriteFilter() {
            @Override public ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) { return null; }
        }));
        assertThrows(IllegalStateException.class, () -> invalidWrite.beforeWrite("db", write()));
    }

    @Test void streamCanRejectAssociationDependentQueriesButIgnoresWriteOnlyFilters() {
        var manager = new SimpFilterManager(List.of(new IWriteFilter() { }));
        assertDoesNotThrow(() -> manager.checkSelectEach("db", query()));
        manager.register(new IQueryFilter() { @Override public boolean supportsSelectEach() { return false; } });
        assertThrows(UnsupportedOperationException.class, () -> manager.checkSelectEach("db", query()));
        assertDoesNotThrow(() -> manager.afterRow("db", query(), new Row()));
    }

    @Test void registrationRejectsFiltersWithoutQueryOrWriteHooksAtomically() {
        var calls = new ArrayList<String>();
        var manager = new SimpFilterManager();
        assertThrows(IllegalArgumentException.class,
                () -> manager.register(List.of(rowFilter(calls, "query", 0), new IFilter() { })));
        manager.afterRow("db", query(), new Row());
        assertTrue(calls.isEmpty(), "failed registration must not install part of the batch");
    }

    private IQueryFilter rowFilter(List<String> calls, String name, int order) {
        return new IQueryFilter() {
            @Override public int order() { return order; }
            @Override public void afterRow(String group, QueryRequest<?> request, Object row) { calls.add(name); }
        };
    }
    static class Row implements IInitEvent {
        int initCalls; String value; String initializedValue;
        @Override public void init() { initCalls++; initializedValue = value; }
    }
}
