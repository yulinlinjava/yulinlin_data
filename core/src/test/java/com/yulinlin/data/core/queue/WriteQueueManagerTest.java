package com.yulinlin.data.core.queue;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.DataProperties;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.lang.util.ThreadUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WriteQueueManagerTest {

    @Test
    void defaultFlushSizeIs1024Nodes() {
        assertThat(new DataProperties.QueueProperties().getFlushSize()).isEqualTo(1024);
    }

    @Test
    void submitReturnsZeroAndFlushExecutesTheQueuedNodes() {
        EntitySession session = session();
        INode node = node();
        ExecuteRequest<?> source = ExecuteRequest.ofInsert(User.class).addRequest(node).queue();
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<ExecuteRequest<?>> executed = new AtomicReference<>();

        try (WriteQueueManager queue = queue(0, (actualSession, request) -> {
            executions.incrementAndGet();
            assertThat(actualSession).isSameAs(session);
            executed.set(request);
            return 7;
        })) {
            assertThat(queue.submit(session, source)).isZero();
            assertThat(queue.pending()).isOne();
            assertThat(executions).hasValue(0);

            assertThat(queue.flush()).isEqualTo(7);
            assertThat(queue.pending()).isZero();
            assertThat(executions).hasValue(1);
            assertThat(executed.get().getRequestType()).isEqualTo(RequestType.insert);
            assertThat(executed.get().getWrappers()).containsExactly(node);
            assertThat(executed.get().isQueued()).isFalse();
        }
    }

    @Test
    void flushGloballyGroupsBySessionOperationAndFromClass() {
        EntitySession session = session();
        INode first = node();
        INode second = node();
        INode third = node();
        ExecuteRequest<?> firstRequest = ExecuteRequest.ofInsert(User.class)
                .addRequest(first)
                .addRequest(second)
                .queue();
        ExecuteRequest<?> secondRequest = ExecuteRequest.ofInsert(User.class)
                .addRequest(third)
                .queue();
        secondRequest.setEntityClass(AnotherView.class);
        secondRequest.setBatch(true);
        ExecuteRequest<?> intervening = ExecuteRequest.ofUpdate(User.class)
                .addRequest(node())
                .queue();
        AtomicInteger executions = new AtomicInteger();
        List<ExecuteRequest<?>> executed = new ArrayList<>();

        try (WriteQueueManager queue = queue(0, (actualSession, request) -> {
            executions.incrementAndGet();
            executed.add(request);
            return request.getWrappers().size();
        })) {
            queue.submit(session, firstRequest);
            queue.submit(session, intervening);
            queue.submit(session, secondRequest);

            assertThat(queue.pending()).isEqualTo(4);
            assertThat(queue.flush()).isEqualTo(4);
            assertThat(executions).hasValue(2);
            assertThat(executed.getFirst().getRequestType()).isEqualTo(RequestType.insert);
            assertThat(executed.getFirst().getWrappers()).containsExactly(first, second, third);
            assertThat(executed.getFirst().isBatch()).isTrue();
            assertThat(executed.get(1).getRequestType()).isEqualTo(RequestType.update);
            assertThat(queue.pending()).isZero();
        }
    }

    @Test
    void flushDoesNotMergeDifferentOperations() {
        EntitySession session = session();
        ExecuteRequest<?> insert = ExecuteRequest.ofInsert(User.class)
                .addRequest(node())
                .queue();
        ExecuteRequest<?> update = ExecuteRequest.ofUpdate(User.class)
                .addRequest(node())
                .queue();
        List<RequestType> operations = new ArrayList<>();

        try (WriteQueueManager queue = queue(0, (actualSession, request) -> {
            operations.add(request.getRequestType());
            return request.getWrappers().size();
        })) {
            queue.submit(session, insert);
            queue.submit(session, update);

            assertThat(queue.flush()).isEqualTo(2);
            assertThat(operations).containsExactly(RequestType.insert, RequestType.update);
            assertThat(queue.pending()).isZero();
        }
    }

    @Test
    void failedBatchIsRetriedOnTheNextFlush() {
        EntitySession session = session();
        ExecuteRequest<?> first = ExecuteRequest.ofDelete(User.class)
                .addRequest(node());
        ExecuteRequest<?> second = ExecuteRequest.ofDelete(User.class)
                .addRequest(node());
        AtomicInteger attempts = new AtomicInteger();

        try (WriteQueueManager queue = queue(1, (actualSession, request) -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("temporary failure");
            }
            return request.getWrappers().size();
        })) {
            queue.submit(session, first);
            queue.submit(session, second);

            assertThat(queue.flush()).isZero();
            assertThat(attempts).hasValue(1);
            assertThat(queue.pending()).isEqualTo(2);

            assertThat(queue.flush()).isEqualTo(2);
            // A failed merged batch is split on retry so one bad request cannot discard its neighbours.
            assertThat(attempts).hasValue(3);
            assertThat(queue.pending()).isZero();

            assertThat(queue.flush()).isZero();
            assertThat(attempts).hasValue(3);
        }
    }

    @Test
    void aFailedGroupDoesNotBlockOtherGroupsAndAcceptsNewWrites() {
        EntitySession session = session();
        ExecuteRequest<?> insert = ExecuteRequest.ofInsert(User.class).addRequest(node());
        ExecuteRequest<?> update = ExecuteRequest.ofUpdate(User.class).addRequest(node());
        ExecuteRequest<?> delete = ExecuteRequest.ofDelete(User.class).addRequest(node());
        List<RequestType> operations = new ArrayList<>();
        AtomicInteger insertAttempts = new AtomicInteger();

        try (WriteQueueManager queue = queue(1, (actualSession, request) -> {
            operations.add(request.getRequestType());
            if (request.getRequestType() == RequestType.insert
                    && insertAttempts.getAndIncrement() == 0) {
                throw new IllegalStateException("temporary failure");
            }
            return request.getWrappers().size();
        })) {
            queue.submit(session, insert);
            queue.submit(session, update);

            assertThat(queue.flush()).isOne();
            queue.submit(session, delete);
            assertThat(queue.flush()).isEqualTo(2);

            assertThat(operations).hasSize(4);
            assertThat(operations).containsOnlyOnce(RequestType.update, RequestType.delete);
            assertThat(operations.stream().filter(type -> type == RequestType.insert)).hasSize(2);
            assertThat(queue.pending()).isZero();
        }
    }

    @Test
    void groupingKeepsDifferentFromClassesAndSessionInstancesSeparate() {
        EntitySession firstSession = equalSession();
        EntitySession secondSession = equalSession();
        ExecuteRequest<?> user = ExecuteRequest.ofInsert(User.class).addRequest(node());
        ExecuteRequest<?> otherSource = ExecuteRequest.ofInsert(User.class).addRequest(node());
        otherSource.setFromClass(AnotherView.class);
        ExecuteRequest<?> otherSession = ExecuteRequest.ofInsert(User.class).addRequest(node());
        AtomicInteger firstSessionExecutions = new AtomicInteger();
        AtomicInteger secondSessionExecutions = new AtomicInteger();
        List<Class<?>> sources = new CopyOnWriteArrayList<>();

        try (WriteQueueManager queue = queue(0, (actualSession, request) -> {
            if (actualSession == firstSession) firstSessionExecutions.incrementAndGet();
            if (actualSession == secondSession) secondSessionExecutions.incrementAndGet();
            sources.add(request.getFromClass());
            return request.getWrappers().size();
        })) {
            queue.submit(firstSession, user);
            queue.submit(firstSession, otherSource);
            queue.submit(secondSession, otherSession);

            assertThat(queue.flush()).isEqualTo(3);
            assertThat(firstSessionExecutions).hasValue(2);
            assertThat(secondSessionExecutions).hasValue(1);
            assertThat(sources).containsExactlyInAnyOrder(User.class, AnotherView.class, User.class);
        }
    }

    @Test
    void differentSessionsRunConcurrently() throws Exception {
        EntitySession first = session();
        EntitySession second = session();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        try (WriteQueueManager queue = queue(0, 4, 10_000, (actualSession, request) -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            started.countDown();
            await(release);
            active.decrementAndGet();
            return request.getWrappers().size();
        })) {
            queue.submit(first, ExecuteRequest.ofInsert(User.class).addRequest(node()));
            queue.submit(second, ExecuteRequest.ofInsert(User.class).addRequest(node()));

            Future<Integer> flush = ThreadUtil.submitVirtual(queue::flush);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum).hasValue(2);
            release.countDown();
            assertThat(flush.get(2, TimeUnit.SECONDS)).isEqualTo(2);
        }
    }

    @Test
    void oneSessionExecutesItsGroupsSequentially() {
        EntitySession session = session();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        try (WriteQueueManager queue = queue(0, 4, 10_000, (actualSession, request) -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(20);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
            return request.getWrappers().size();
        })) {
            queue.submit(session, ExecuteRequest.ofInsert(User.class).addRequest(node()));
            queue.submit(session, ExecuteRequest.ofUpdate(User.class).addRequest(node()));
            queue.submit(session, ExecuteRequest.ofDelete(User.class).addRequest(node()));

            assertThat(queue.flush()).isEqualTo(3);
            assertThat(maximum).hasValue(1);
        }
    }

    @Test
    void sessionConcurrencyNeverExceedsConfiguredLimit() throws Exception {
        CountDownLatch firstWorkers = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();

        try (WriteQueueManager queue = queue(0, 2, 10_000, (actualSession, request) -> {
            executions.incrementAndGet();
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            firstWorkers.countDown();
            await(release);
            active.decrementAndGet();
            return request.getWrappers().size();
        })) {
            for (int index = 0; index < 5; index++) {
                queue.submit(session(), ExecuteRequest.ofInsert(User.class).addRequest(node()));
            }

            Future<Integer> flush = ThreadUtil.submitVirtual(queue::flush);
            assertThat(firstWorkers.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum).hasValue(2);
            assertThat(executions).hasValue(2);
            release.countDown();
            assertThat(flush.get(2, TimeUnit.SECONDS)).isEqualTo(5);
            assertThat(maximum).hasValue(2);
        }
    }

    @Test
    void aFullGroupTriggersEarlyFlushWithoutSplittingTheRequest() throws Exception {
        EntitySession session = session();
        CountDownLatch executed = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger nodes = new AtomicInteger();

        try (WriteQueueManager queue = queue(0, 4, 2, (actualSession, request) -> {
            executions.incrementAndGet();
            nodes.set(request.getWrappers().size());
            executed.countDown();
            return request.getWrappers().size();
        })) {
            ExecuteRequest<?> request = ExecuteRequest.ofInsert(User.class);
            for (int index = 0; index < 5; index++) request.addRequest(node());
            queue.submit(session, request);

            assertThat(executed.await(2, TimeUnit.SECONDS)).isTrue();
            queue.flush(); // Wait for the threshold-triggered flush if it is still completing.
            assertThat(executions).hasValue(1);
            assertThat(nodes).hasValue(5);
            assertThat(queue.pending()).isZero();
        }
    }

    @Test
    void flushSizeCountsAllActiveNodesAcrossDifferentGroups() throws Exception {
        EntitySession session = session();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch executed = new CountDownLatch(2);

        try (WriteQueueManager queue = queue(0, 4, 2, (actualSession, request) -> {
            executions.incrementAndGet();
            executed.countDown();
            return request.getWrappers().size();
        })) {
            queue.submit(session, ExecuteRequest.ofInsert(User.class).addRequest(node()));
            queue.submit(session, ExecuteRequest.ofUpdate(User.class).addRequest(node()));

            assertThat(executed.await(2, TimeUnit.SECONDS)).isTrue();
            queue.flush(); // Wait for the threshold-triggered flush if it is still completing.
            assertThat(executions).hasValue(2);
            assertThat(queue.pending()).isZero();
        }
    }

    @Test
    void reachingFlushSizeDuringAFlushSchedulesTheNextSnapshot() throws Exception {
        EntitySession first = session();
        EntitySession second = session();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondExecuted = new CountDownLatch(1);

        try (WriteQueueManager queue = queue(0, 1, 1, (actualSession, request) -> {
            if (actualSession == first) {
                firstStarted.countDown();
                await(releaseFirst);
            } else if (actualSession == second) {
                secondExecuted.countDown();
            }
            return request.getWrappers().size();
        })) {
            queue.submit(first, ExecuteRequest.ofInsert(User.class).addRequest(node()));
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();

            queue.submit(second, ExecuteRequest.ofInsert(User.class).addRequest(node()));
            releaseFirst.countDown();

            assertThat(secondExecuted.await(2, TimeUnit.SECONDS)).isTrue();
            queue.flush();
            assertThat(queue.pending()).isZero();
        }
    }

    @Test
    void anEarlyFlushFailureWaitsForTheNextFlushBeforeRetrying() throws Exception {
        EntitySession session = session();
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstAttempt = new CountDownLatch(1);

        try (WriteQueueManager queue = queue(1, 1, 1, (actualSession, request) -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                firstAttempt.countDown();
                throw new IllegalStateException("temporary failure");
            }
            return request.getWrappers().size();
        })) {
            queue.submit(session, ExecuteRequest.ofInsert(User.class).addRequest(node()));
            assertThat(firstAttempt.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(attempts).hasValue(1);
            assertThat(queue.pending()).isOne();

            assertThat(queue.flush()).isOne();
            assertThat(attempts).hasValue(2);
            assertThat(queue.pending()).isZero();
        }
    }

    private static WriteQueueManager queue(int retryCount, WriteQueueManager.Executor executor) {
        return queue(retryCount, 4, 10_000, executor);
    }

    private static WriteQueueManager queue(int retryCount, int concurrency, int flushSize,
                                           WriteQueueManager.Executor executor) {
        DataProperties.QueueProperties properties = new DataProperties.QueueProperties();
        properties.setFlushInterval(Duration.ofDays(1));
        properties.setRetryCount(retryCount);
        properties.setConcurrency(concurrency);
        properties.setFlushSize(flushSize);
        WriteQueueManager queue = new WriteQueueManager(properties);
        queue.setExecutor(executor);
        return queue;
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException error) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static INode node() {
        return new INode() {
        };
    }

    private static EntitySession session() {
        return (EntitySession) Proxy.newProxyInstance(
                EntitySession.class.getClassLoader(),
                new Class<?>[]{EntitySession.class},
                (proxy, method, arguments) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    return null;
                });
    }

    private static EntitySession equalSession() {
        return (EntitySession) Proxy.newProxyInstance(
                EntitySession.class.getClassLoader(),
                new Class<?>[]{EntitySession.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> true;
                    case "hashCode" -> 1;
                    default -> {
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) yield false;
                        if (type == int.class) yield 0;
                        yield null;
                    }
                });
    }

    private static final class User {
    }

    private static final class AnotherView {
    }
}
