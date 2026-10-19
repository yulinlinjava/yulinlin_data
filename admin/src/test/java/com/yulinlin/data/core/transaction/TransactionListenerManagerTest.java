package com.yulinlin.data.core.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransactionListenerManagerTest {
    @AfterEach
    void cleanTransaction() {
        while (TransactionListenerManager.currentContext() != null)
            TransactionListenerManager.currentContext().manager().rollbackTransaction();
    }

    @Test
    void onlyTheOutermostCommitNotifiesListenersAndRunsCallbacks() {
        TransactionListener first = mock(TransactionListener.class);
        TransactionListener second = mock(TransactionListener.class);
        TransactionListenerManager manager = new TransactionListenerManager(List.of(first, second));

        manager.startTransaction();
        manager.startTransaction();
        manager.commitTransaction();
        verify(first, never()).commitTransaction();
        verify(second, never()).commitTransaction();
        verify(first, never()).afterCommit();
        verify(second, never()).afterCommit();

        manager.commitTransaction();
        verify(first).startTransaction();
        verify(second).startTransaction();
        verify(first).commitTransaction();
        verify(second).commitTransaction();
        verify(first).afterCompletion();
        verify(second).afterCompletion();
        verify(first).afterCommit();
        verify(second).afterCommit();
    }

    @Test
    void defaultAfterCommitKeepsExistingListenerImplementationsCompatible() {
        AtomicInteger completions = new AtomicInteger();
        TransactionListener existing = new TransactionListener() {
            @Override public void startTransaction() { }
            @Override public void commitTransaction() { }
            @Override public void rollbackTransaction() { }
            @Override public void afterCompletion() { completions.incrementAndGet(); }
        };
        TransactionListenerManager manager = new TransactionListenerManager(List.of(existing));
        manager.afterCommit();
        manager.afterCompletion();
        assertThat(completions.get()).isEqualTo(1);
    }

    @Test
    void listenerFailureDoesNotSkipRemainingAfterCommitListeners() {
        TransactionListener first = mock(TransactionListener.class);
        TransactionListener second = mock(TransactionListener.class);
        IllegalStateException error = new IllegalStateException("监听器失败");
        doThrow(error).when(first).afterCommit();

        TransactionListenerManager manager = new TransactionListenerManager(List.of(first, second));
        assertThatThrownBy(manager::afterCommit).isSameAs(error);
        verify(second).afterCommit();
    }

    @Test
    void beforeCommitHooksCanRegisterOneBusinessCallbackWithoutOpeningAnotherScope() {
        java.util.ArrayList<String> events = new java.util.ArrayList<>();
        TransactionListener listener = new TransactionListener() {
            @Override public void startTransaction() { }
            @Override public void commitTransaction() {
                events.add("beforeCommit");
                TransactionUtil.afterCommit(() -> events.add("task"));
            }
            @Override public void rollbackTransaction() { }
            @Override public void afterCompletion() { events.add("completion"); }
            @Override public void afterCommit() { events.add("afterCommit"); }
        };
        TransactionListenerManager manager = new TransactionListenerManager(List.of(listener));
        manager.startTransaction();
        manager.commitTransaction();
        assertThat(events).containsExactly("beforeCommit", "completion", "afterCommit", "task");
    }
}
