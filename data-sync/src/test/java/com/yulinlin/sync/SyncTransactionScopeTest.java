package com.yulinlin.sync;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.wrapper.impl.InsertWrapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SyncTransactionScopeTest {
    public static class Row { public Integer id; }
    private ExecuteRequest<Row> request(boolean batch) {
        var wrapper = new InsertWrapper<Row>().table("rows");
        wrapper.fields().field("id", 1);
        ExecuteRequest<Row> request = ExecuteRequest.ofInsert(Row.class);
        request.addRequest(wrapper);
        request.setBatch(batch);
        return request;
    }
    private SyncManager manager(MemorySyncTaskStore store) {
        var properties = new DataSyncProperties();
        var rule = new DataSyncProperties.Rule();
        rule.setName("test"); rule.setSource("source"); rule.setTarget("target");
        rule.setEntities(List.of(Row.class.getName()));
        properties.setRules(List.of(rule));
        return new SyncManager(properties, store, task -> fail("worker must not start in this test"));
    }
    @Test void nestedRollbackDiscardsSnapshotAndRootCommitEnqueuesSuccessfulScope() {
        var store = new MemorySyncTaskStore();
        try (var manager = manager(store)) {
            var route = RouteSession.builder().build();
            route.transaction(() -> {
                assertThrows(IllegalArgumentException.class, () -> route.transaction(() -> {
                    manager.capture("source", request(false));
                    throw new IllegalArgumentException("rollback nested");
                }));
                manager.capture("source", request(false));
                assertEquals(0, store.pendingTasks("test"));
                return null;
            });
            assertEquals(1, store.pendingTasks("test"));
        }
    }
    @Test void successfulIndependentWriteDoesNotRegisterAMissingTransactionCallback() {
        var store = new MemorySyncTaskStore();
        try (var manager = manager(store)) {
            manager.capture("source", request(false));
            assertEquals(1, store.pendingTasks("test"));
        }
    }
    @Test void matchedParallelSourceRequiresExplicitAtomicScopeBeforeWriting() {
        try (var manager = manager(new MemorySyncTaskStore())) {
            var filter = new SyncRequestFilter(manager);
            assertThrows(IllegalStateException.class, () -> filter.updateBefore("source", request(true)));
            assertDoesNotThrow(() -> filter.updateBefore("other", request(true)));
            var rerouted = request(true);
            rerouted.setSession("source");
            assertThrows(IllegalStateException.class, () -> filter.updateBefore("other", rerouted));
            assertDoesNotThrow(() -> filter.updateBefore("source", request(false)));
            RouteSession.builder().build().transaction(() -> {
                assertDoesNotThrow(() -> filter.updateBefore("source", request(true)));
                return null;
            });
        }
    }
}
