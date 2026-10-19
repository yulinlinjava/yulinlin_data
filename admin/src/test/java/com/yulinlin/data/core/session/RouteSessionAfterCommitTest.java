package com.yulinlin.data.core.session;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.transaction.TransactionListener;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.data.core.transaction.TransactionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 生命周期契约用例，不访问真实数据库。 */
class RouteSessionAfterCommitTest {
    private final List<String> events = new ArrayList<>();
    private RouteSession previous;
    private RouteSession route;
    private EntitySession database;
    private TransactionListenerManager listeners;

    @BeforeEach
    void prepare() {
        previous = SessionUtil.route();
        TransactionListener listener = new TransactionListener() {
            @Override public void startTransaction() { events.add("start"); }
            @Override public void commitTransaction() { events.add("beforeCommit"); }
            @Override public void rollbackTransaction() { events.add("beforeRollback"); }
            @Override public void afterCommit() { events.add("afterCommit"); }
            @Override public void afterCompletion() { events.add("completion"); }
        };
        listeners = new TransactionListenerManager(List.of(listener));
        route = RouteSession.builder().transactionListenerManager(listeners).build();
        route.setLoadBalance(new RandomLoadBalance());
        database = mock(EntitySession.class);
        when(database.group()).thenReturn("local");
        when(database.cluster()).thenReturn(JoinCluster.master);
        when(database.weight()).thenReturn(1);
        doAnswer(invocation -> { events.add("databaseCommit"); return null; }).when(database).commitTransaction();
        route.registerSession(database);
        new SessionUtil(route);
    }

    @AfterEach
    void restore() {
        if (route.isOpenTransaction()) route.rollbackTransaction();
        new SessionUtil(previous);
    }

    @Test
    void notifiesOnlyAfterParticipantsCommitAndPreservesCallbackOrder() {
        route.transaction(() -> {
            route.session("local");
            TransactionUtil.afterCommit(() -> events.add("first"));
            TransactionUtil.afterCommit(() -> events.add("second"));
            assertThat(events).containsExactly("start");
            return null;
        });

        assertThat(events).containsExactly("start", "beforeCommit", "databaseCommit",
                "completion", "afterCommit", "first", "second");
        assertThat(route.isOpenTransaction()).isFalse();
    }

    @Test
    void nestedRouteCommitsWaitForTheOutermostTransaction() {
        route.transaction(() -> {
            Object original = route.transactionIdentity();
            route.session("local");
            TransactionUtil.afterCommit(() -> events.add("outer"));
            route.transaction(() -> {
                assertThat(route.transactionIdentity()).isSameAs(original);
                route.session("local");
                TransactionUtil.afterCommit(() -> events.add("inner"));
                return null;
            });
            assertThat(route.transactionIdentity()).isSameAs(original);
            assertThat(route.transactionDepth()).isEqualTo(1);
            assertThat(events).containsExactly("start");
            return null;
        });
        assertThat(events).filteredOn(event -> List.of("afterCommit", "outer", "inner", "completion").contains(event))
                .containsExactly("completion", "afterCommit", "outer", "inner");
        assertThat(events).filteredOn("start"::equals).hasSize(1);
        assertThat(events).filteredOn("beforeCommit"::equals).hasSize(1);
        verify(database).startTransaction();
        verify(database).commitTransaction();
    }

    @Test
    void rollbackAndRollbackOnlyDiscardCallbacks() {
        assertThatThrownBy(() -> route.transaction(() -> {
            TransactionUtil.afterCommit(() -> events.add("mustNotRun"));
            throw new IllegalArgumentException("业务失败");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(events).doesNotContain("afterCommit", "mustNotRun");

        events.clear();
        route.startTransaction();
        TransactionUtil.afterCommit(() -> events.add("mustNotRun"));
        route.startTransaction();
        route.rollbackTransaction();
        assertThat(events).containsExactly("start");
        assertThat(route.isRollbackOnly()).isTrue();
        assertThatThrownBy(route::commitTransaction).isInstanceOf(IllegalStateException.class);
        assertThat(events).doesNotContain("afterCommit", "mustNotRun");
        assertThat(events).filteredOn("completion"::equals).hasSize(1);
    }

    @Test
    void listenerFlushFailureRollsBackParticipantsAndClearsTheContext() {
        TransactionListener failing = mock(TransactionListener.class);
        RuntimeException failure = new IllegalStateException("代理写回失败");
        doThrow(failure).when(failing).commitTransaction();
        listeners.add(failing);

        assertThatThrownBy(() -> route.transaction(() -> {
            route.session("local");
            TransactionUtil.afterCommit(() -> events.add("mustNotRun"));
            return null;
        })).isSameAs(failure);

        verify(database, never()).commitTransaction();
        verify(database).rollbackTransaction();
        verify(failing).rollbackTransaction();
        verify(failing).afterCompletion();
        assertThat(route.transactionIdentity()).isNull();
        assertThat(events).doesNotContain("afterCommit", "mustNotRun");
    }

    @Test
    void rollbackMarkerFailureStillReleasesTheContextAndRollsBackParticipants() {
        RuntimeException failure = new IllegalStateException("回滚标记失败");
        doThrow(failure).when(database).setRollbackOnly();
        route.startTransaction();
        Object original = route.transactionIdentity();
        route.session("local");
        TransactionUtil.afterCommit(() -> events.add("mustNotRun"));

        assertThatThrownBy(route::rollbackTransaction).isSameAs(failure);
        verify(database).rollbackTransaction();
        assertThat(route.transactionIdentity()).isNull();
        assertThat(events).doesNotContain("afterCommit", "mustNotRun");
        assertThat(events).filteredOn("completion"::equals).hasSize(1);

        route.transaction(() -> {
            assertThat(route.transactionIdentity()).isNotSameAs(original);
            assertThat(route.isRollbackOnly()).isFalse();
            return null;
        });
    }

    @Test
    void businessFailureStillClosesTheContextWhenSettingRollbackOnlyFails() {
        RuntimeException businessFailure = new IllegalArgumentException("业务失败");
        RuntimeException markerFailure = new IllegalStateException("回滚标记失败");
        doThrow(markerFailure).when(database).setRollbackOnly();

        assertThatThrownBy(() -> route.transaction(() -> {
            route.session("local");
            throw businessFailure;
        })).isSameAs(businessFailure)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(markerFailure));
        verify(database).rollbackTransaction();
        assertThat(route.transactionIdentity()).isNull();
        assertThat(events).filteredOn("completion"::equals).hasSize(1);
    }

    @Test
    void failedParticipantCommitDoesNotReportSuccessfulCommit() {
        doThrow(new IllegalStateException("提交失败")).when(database).commitTransaction();
        assertThatThrownBy(() -> route.transaction(() -> {
            route.session("local");
            TransactionUtil.afterCommit(() -> events.add("mustNotRun"));
            return null;
        })).isInstanceOf(IllegalStateException.class).hasMessage("提交失败");
        assertThat(events).doesNotContain("afterCommit", "mustNotRun");
        assertThat(events).filteredOn("completion"::equals).hasSize(1);
    }

    @Test
    void callbackFailuresDoNotRollbackCommittedDataOrSkipFollowingTasks() {
        RuntimeException first = new IllegalArgumentException("第一个回调失败");
        RuntimeException second = new IllegalStateException("第二个回调失败");
        assertThatThrownBy(() -> route.transaction(() -> {
            route.session("local");
            TransactionUtil.afterCommit(() -> { throw first; });
            TransactionUtil.afterCommit(() -> events.add("followingTask"));
            TransactionUtil.afterCommit(() -> { throw second; });
            return null;
        })).isSameAs(first).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(second));
        assertThat(events).contains("afterCommit", "followingTask", "completion");
        verify(database).commitTransaction();
        verify(database, never()).rollbackTransaction();
        assertThat(route.isOpenTransaction()).isFalse();

        events.clear();
        route.transaction(() -> null);
        assertThat(events).containsExactly("start", "beforeCommit", "completion", "afterCommit");
    }

    @Test
    void callbacksCanStartANewFrameworkTransactionAfterTheOldContextIsCleared() {
        ThreadLocal<Object> context = new ThreadLocal<>();
        listeners.add(new TransactionListener() {
            @Override public void startTransaction() {
                assertThat(context.get()).isNull();
                context.set(route.transactionIdentity());
            }
            @Override public void commitTransaction() { }
            @Override public void rollbackTransaction() { }
            @Override public void afterCompletion() { context.remove(); }
            @Override public void afterCommit() {
                assertThat(context.get()).isNull();
                assertThat(route.isOpenTransaction()).isFalse();
            }
        });

        route.transaction(() -> {
            Object original = route.transactionIdentity();
            route.session("local");
            TransactionUtil.afterCommit(() -> {
                assertThat(context.get()).isNull();
                events.add("firstCallback");
                route.transaction(() -> {
                    assertThat(route.transactionIdentity()).isNotSameAs(original);
                    assertThat(context.get()).isSameAs(route.transactionIdentity());
                    route.session("local");
                    TransactionUtil.afterCommit(() -> events.add("newTransactionTask"));
                    return null;
                });
            });
            TransactionUtil.afterCommit(() -> {
                assertThat(context.get()).isNull();
                assertThat(route.isOpenTransaction()).isFalse();
                events.add("secondCallback");
            });
            return null;
        });

        assertThat(events).containsExactly("start", "beforeCommit", "databaseCommit", "completion", "afterCommit",
                "firstCallback", "start", "beforeCommit", "databaseCommit", "completion", "afterCommit",
                "newTransactionTask", "secondCallback");
        verify(database, times(2)).commitTransaction();
        verify(database, never()).rollbackTransaction();
        assertThat(route.isOpenTransaction()).isFalse();
        assertThat(context.get()).isNull();
    }

    @Test
    void cleanupFailureAfterCommitDoesNotSkipListenersOrBusinessCallbacks() {
        TransactionListener cleanup = mock(TransactionListener.class);
        RuntimeException cleanupFailure = new IllegalStateException("清理失败");
        RuntimeException callbackFailure = new IllegalArgumentException("回调失败");
        doThrow(cleanupFailure).when(cleanup).afterCompletion();
        listeners.add(cleanup);

        assertThatThrownBy(() -> route.transaction(() -> {
            route.session("local");
            TransactionUtil.afterCommit(() -> { throw callbackFailure; });
            TransactionUtil.afterCommit(() -> events.add("followingTask"));
            return null;
        })).isSameAs(cleanupFailure)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(callbackFailure));

        assertThat(events).containsSubsequence("databaseCommit", "completion", "afterCommit", "followingTask");
        verify(cleanup).afterCompletion();
        verify(cleanup).afterCommit();
        verify(database).commitTransaction();
        verify(database, never()).rollbackTransaction();
        assertThat(route.isOpenTransaction()).isFalse();
    }

    @Test
    void fixedListenerFailureDoesNotSkipBusinessCallbacks() {
        TransactionListener failing = mock(TransactionListener.class);
        TransactionListener following = mock(TransactionListener.class);
        RuntimeException failure = new IllegalStateException("监听器失败");
        doThrow(failure).when(failing).afterCommit();
        listeners.add(failing).add(following);

        assertThatThrownBy(() -> route.transaction(() -> {
            route.session("local");
            TransactionUtil.afterCommit(() -> events.add("businessTask"));
            return null;
        })).isSameAs(failure);

        verify(following).afterCommit();
        assertThat(events).containsSubsequence("databaseCommit", "completion", "afterCommit", "businessTask");
        verify(database, never()).rollbackTransaction();
    }

    @Test
    void rejectsRegistrationWithoutAnActiveTransaction() {
        assertThatThrownBy(() -> TransactionUtil.afterCommit(() -> events.add("mustNotRun")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("活动事务");
        assertThat(events).isEmpty();
    }

    @Test
    void rejectsRegistrationWithoutAConfiguredRouteEvenWhenSpringIsActive() {
        new SessionUtil(null);
        try {
            spring().execute(status -> {
                assertThatThrownBy(() -> TransactionUtil.afterCommit(() -> events.add("mustNotRun")))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("框架事务");
                return null;
            });
        } finally {
            new SessionUtil(route);
        }
        assertThat(events).doesNotContain("mustNotRun", "afterCommit");
    }

    @Test
    void rejectsNullCallbacks() {
        assertThatThrownBy(() -> TransactionUtil.afterCommit(null)).isInstanceOf(NullPointerException.class);
        route.transaction(() -> {
            assertThatThrownBy(() -> TransactionUtil.afterCommit(null)).isInstanceOf(NullPointerException.class);
            return null;
        });
    }

    @Test
    void routerAndDirectManagerShareTheSameOutermostContext() {
        listeners.startTransaction();
        Object original = route.transactionIdentity();
        listeners.enlist(database);
        route.transaction(() -> {
            assertThat(route.transactionIdentity()).isSameAs(original);
            route.session("local");
            TransactionUtil.afterCommit(() -> events.add("task"));
            return null;
        });
        assertThat(events).containsExactly("start");
        listeners.commitTransaction();
        assertThat(events).containsSubsequence("databaseCommit", "completion", "afterCommit", "task");
    }

    @Test
    void routeInsideSpringWaitsForTheActualSpringCommit() {
        TransactionTemplate spring = spring();
        spring.execute(status -> {
            route.transaction(() -> {
                TransactionUtil.afterCommit(() -> events.add("task"));
                return null;
            });
            assertThat(events).contains("completion").doesNotContain("task", "afterCommit");
            return null;
        });
        assertThat(events).containsSubsequence("completion", "springCommit", "afterCommit", "task");
        assertThat(events).filteredOn("afterCommit"::equals).hasSize(1);
        assertThat(events).filteredOn("completion"::equals).hasSize(1);
    }

    @Test
    void deferredSpringNotificationCannotCleanTheNextRouteTransactionContext() {
        ThreadLocal<Object> context = new ThreadLocal<>();
        listeners.add(new TransactionListener() {
            @Override public void startTransaction() { context.set(route.transactionIdentity()); }
            @Override public void commitTransaction() { }
            @Override public void rollbackTransaction() { }
            @Override public void afterCompletion() { context.remove(); }
        });

        spring().execute(status -> {
            route.transaction(() -> {
                TransactionUtil.afterCommit(() -> events.add("firstTransactionTask"));
                return null;
            });
            assertThat(context.get()).isNull();
            route.startTransaction();
            TransactionUtil.afterCommit(() -> events.add("secondTransactionTask"));
            assertThat(context.get()).isSameAs(route.transactionIdentity());
            return null;
        });

        // 第一笔路由事务的延迟通知不能清理仍活动的第二笔路由事务。
        assertThat(route.isOpenTransaction()).isTrue();
        assertThat(context.get()).isSameAs(route.transactionIdentity());
        assertThat(events).contains("firstTransactionTask").doesNotContain("secondTransactionTask");
        assertThat(events).filteredOn("completion"::equals).hasSize(1);

        route.commitTransaction();
        assertThat(context.get()).isNull();
        assertThat(events).filteredOn("completion"::equals).hasSize(2);
        assertThat(events).filteredOn("afterCommit"::equals).hasSize(2);
        assertThat(events).containsSubsequence("firstTransactionTask", "secondTransactionTask");
    }

    @Test
    void routeAroundSpringNotifiesOnlyOnceAfterBothBoundariesFinish() {
        route.transaction(() -> spring().execute(status -> {
            TransactionUtil.afterCommit(() -> events.add("task"));
            return null;
        }));
        assertThat(events).containsSubsequence("beforeCommit", "springCommit", "springCleanup",
                "completion", "afterCommit", "task");
        assertThat(events).filteredOn("afterCommit"::equals).hasSize(1);
    }

    @Test
    void springRollbackAfterTheRouteScopeClosesStillDiscardsItsTasks() {
        spring().execute(status -> {
            route.transaction(() -> {
                TransactionUtil.afterCommit(() -> events.add("mustNotRun"));
                return null;
            });
            status.setRollbackOnly();
            return null;
        });
        assertThat(events).containsSubsequence("completion", "springRollback");
        assertThat(events).doesNotContain("mustNotRun", "afterCommit");
    }

    @Test
    void springCommitFailureDoesNotProduceASuccessNotification() {
        assertThatThrownBy(() -> spring(true).execute(status -> {
            route.transaction(() -> {
                TransactionUtil.afterCommit(() -> events.add("mustNotRun"));
                return null;
            });
            return null;
        })).isInstanceOf(IllegalStateException.class).hasMessage("Spring 提交失败");
        assertThat(events).doesNotContain("afterCommit", "mustNotRun");
        assertThat(events).filteredOn("completion"::equals).hasSize(1);
    }

    @Test
    void plainSpringTransactionsCannotRegisterFrameworkCallbacks() {
        spring().execute(status -> {
            assertThatThrownBy(() -> TransactionUtil.afterCommit(() -> events.add("mustNotRun")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("框架事务");
            return null;
        });
        assertThat(events).containsExactly("springCommit", "springCleanup");

        events.clear();
        route.transaction(() -> null);
        assertThat(events).doesNotContain("mustNotRun");
    }

    @Test
    void routedSpringCallbackFailureCannotRollbackTheAlreadyCommittedTransaction() {
        RuntimeException failure = new IllegalStateException("回调失败");
        assertThatThrownBy(() -> route.transaction(() -> spring().execute(status -> {
            TransactionUtil.afterCommit(() -> { throw failure; });
            TransactionUtil.afterCommit(() -> events.add("followingTask"));
            return null;
        }))).isSameAs(failure);
        assertThat(events).containsSubsequence("springCommit", "completion", "afterCommit", "followingTask")
                .doesNotContain("springRollback", "beforeRollback");
    }

    private TransactionTemplate spring() {
        return spring(false);
    }

    private TransactionTemplate spring(boolean failCommit) {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if (failCommit) throw new IllegalStateException("Spring 提交失败");
                events.add("springCommit");
            }
            @Override protected void doRollback(DefaultTransactionStatus status) { events.add("springRollback"); }
            @Override protected void doCleanupAfterCompletion(Object transaction) { events.add("springCleanup"); }
        });
    }
}
