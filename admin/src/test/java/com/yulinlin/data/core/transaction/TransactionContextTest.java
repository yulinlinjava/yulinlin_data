package com.yulinlin.data.core.transaction;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.loadbalan.LoadBalance;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 共享逻辑事务和 Session 资源事务的边界，不访问数据库。 */
class TransactionContextTest {
    private RouteSession previous;

    @BeforeEach void prepare() { previous = SessionUtil.route(); new SessionUtil(null); }
    @AfterEach void clean() {
        try {
            while (TransactionListenerManager.currentContext() != null)
                TransactionListenerManager.currentContext().manager().rollbackTransaction();
        } finally { new SessionUtil(previous); }
    }

    @Test void onlyRouteNestingChangesDepthAndResourcesFinishAtTheOutermostBoundary() {
        TransactionListener listener = mock(TransactionListener.class);
        TransactionListenerManager manager = new TransactionListenerManager(List.of(listener));
        EntitySession first = mock(EntitySession.class);
        EntitySession second = mock(EntitySession.class);
        RouteSession route = RouteSession.builder().transactionListenerManager(manager).build();
        LoadBalance balance = mock(LoadBalance.class);
        when(balance.loadBalance("first", JoinCluster.master)).thenReturn(first);
        when(balance.loadBalance("second", JoinCluster.master)).thenReturn(second);
        route.setLoadBalance(balance);

        route.startTransaction();
        TransactionContext original = TransactionListenerManager.currentContext();
        assertThat(original.proxyChanges()).isNull();
        route.session("first");
        route.startTransaction();
        route.session("second");
        route.session("first");
        assertThat(TransactionListenerManager.currentContext()).isSameAs(original);
        assertThat(original.depth()).isEqualTo(2);

        route.commitTransaction();
        verify(first, never()).commitTransaction();
        verify(second, never()).commitTransaction();
        verify(listener, never()).commitTransaction();
        route.commitTransaction();

        assertThat(TransactionListenerManager.currentContext()).isNull();
        verify(first).startTransaction();
        verify(second).startTransaction();
        verify(first).commitTransaction();
        verify(second).commitTransaction();
        verify(listener).startTransaction();
        verify(listener).commitTransaction();
        verify(listener).afterCompletion();
        verify(listener).afterCommit();
    }

    @Test void resourceLifecycleDoesNotCreateBusinessContext() {
        var session = mock(com.yulinlin.data.core.session.AbstractSession.class,
                withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
        try {
            session.startTransaction();
            session.commitTransaction();
            session.rollbackTransaction();
            assertThat(TransactionListenerManager.currentContext()).isNull();
        } finally { session.getThreadPoolExecutor().shutdownNow(); }
    }

    @Test void contextIsThreadConfinedAndCreatedAgainAfterCompletion() {
        RouteSession route = RouteSession.builder().build();
        route.startTransaction();
        TransactionContext original = TransactionListenerManager.currentContext();
        TransactionContext worker = CompletableFuture.supplyAsync(() -> {
            assertThat(TransactionListenerManager.currentContext()).isNull();
            route.startTransaction();
            try { return TransactionListenerManager.currentContext(); }
            finally { route.commitTransaction(); }
        }).join();
        assertThat(worker).isNotSameAs(original);
        assertThat(TransactionListenerManager.currentContext()).isSameAs(original);
        route.commitTransaction();
        route.startTransaction();
        assertThat(TransactionListenerManager.currentContext()).isNotSameAs(original);
        route.commitTransaction();
    }

    @Test void callbacksUseTheActiveRouteContextWithoutRequiringTheGlobalFacade() {
        RouteSession route = RouteSession.builder().build();
        route.startTransaction();
        java.util.ArrayList<String> events = new java.util.ArrayList<>();
        TransactionUtil.afterCommit(() -> {
            assertThat(TransactionListenerManager.currentContext()).isNull();
            events.add("committed");
        });
        route.commitTransaction();
        assertThat(events).containsExactly("committed");
    }
}
