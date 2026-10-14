package com.yulinlin.data.core.log;

import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogManagerTest {

    @Test
    void customHandlerSelectionIsEvaluatedPerRequestAndRegistrationRemainsVisible() {
        RecordingPrint first = new RecordingPrint();
        RecordingPrint second = new RecordingPrint();
        LogManager manager = new LogManager().register(first);

        manager.success(1, result("first"));
        manager.success(1, result("second"));

        assertThat(first.handleChecks).hasValue(2);
        assertThat(first.successes).hasValue(2);

        manager.register(second);
        manager.success(1, result("third"));

        assertThat(first.handleChecks).hasValue(3);
        assertThat(second.handleChecks).hasValue(1);
        assertThat(first.successes).hasValue(3);
        assertThat(second.successes).hasValue(1);
    }

    @Test
    void isolatesRuntimeFailuresFromEveryHandlerHook() {
        RecordingPrint recording = new RecordingPrint();
        LogManager manager = new LogManager()
                .register(new ThrowingPrint())
                .register(recording);
        List<ParseResult> batch = List.of(result("one"), result("two"));

        assertThatCode(() -> manager.success(12, batch.getFirst())).doesNotThrowAnyException();
        assertThatCode(() -> manager.error(new IllegalStateException("database"), batch.getFirst()))
                .doesNotThrowAnyException();
        assertThatCode(() -> manager.successBatch(12, batch, 2, null)).doesNotThrowAnyException();
        assertThatCode(() -> manager.errorBatch(new IllegalStateException("database"), batch, null))
                .doesNotThrowAnyException();

        assertThat(recording.successes).hasValue(1);
        assertThat(recording.errors).hasValue(1);
        assertThat(recording.successBatches).hasValue(1);
        assertThat(recording.errorBatches).hasValue(1);
        assertThat(recording.lastBatchSize).hasValue(2);
    }

    @Test
    void isolatesDynamicHandlerSelectionFailureForEveryRequest() {
        FailingSelectionPrint failing = new FailingSelectionPrint();
        RecordingPrint recording = new RecordingPrint();
        LogManager manager = new LogManager().register(failing).register(recording);

        assertThatCode(() -> {
            manager.success(1, result("one"));
            manager.success(1, result("two"));
        }).doesNotThrowAnyException();

        assertThat(failing.handleChecks).hasValue(2);
        assertThat(recording.successes).hasValue(2);
    }

    @Test
    void batchDefaultsKeepLegacyHandlersCompatible() {
        LegacyPrint legacy = new LegacyPrint();
        List<ParseResult> results = Arrays.asList(result("one"), null, result("two"));

        legacy.successBatch(10, results, 2, null);
        legacy.errorBatch(new IllegalArgumentException("failed"), results, null);

        assertThat(legacy.successes).hasValue(2);
        assertThat(legacy.lastSuccessElapsedMs).hasValue(5);
        assertThat(legacy.errors).hasValue(2);
    }

    @Test
    void ignoresNullRegistrationsAndRequests() {
        LogManager manager = new LogManager();

        assertThatCode(() -> manager.register((LogPrint<?>) null)
                .register((List<? extends LogPrint<?>>) null)
                .register(Arrays.<LogPrint<?>>asList(null, null))
                .success(1, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> manager.error(null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> manager.successBatch(1, Arrays.asList(null, null), 0, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> manager.errorBatch(null, null, null)).doesNotThrowAnyException();
    }

    private static ParseResult result(Object request) {
        return new ParseResult(null, request, null);
    }

    private static class RecordingPrint implements LogPrint<Object> {
        private final AtomicInteger handleChecks = new AtomicInteger();
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicInteger successBatches = new AtomicInteger();
        private final AtomicInteger errorBatches = new AtomicInteger();
        private final AtomicInteger lastBatchSize = new AtomicInteger();

        @Override
        public boolean isHandle(ParseResult result) {
            handleChecks.incrementAndGet();
            return result != null && result.getRequest() instanceof String;
        }

        @Override
        public void success(long time, ParseResult request) {
            successes.incrementAndGet();
        }

        @Override
        public void error(Throwable e, ParseResult request) {
            errors.incrementAndGet();
        }

        @Override
        public void successBatch(long elapsedMs, List<ParseResult> results, int affected, EntitySession session) {
            successBatches.incrementAndGet();
            lastBatchSize.set(results.size());
        }

        @Override
        public void errorBatch(Throwable error, List<ParseResult> results, EntitySession session) {
            errorBatches.incrementAndGet();
            lastBatchSize.set(results.size());
        }
    }

    private static final class ThrowingPrint extends RecordingPrint {
        @Override
        public void success(long time, ParseResult request) {
            throw new IllegalStateException("success logging failed");
        }

        @Override
        public void error(Throwable e, ParseResult request) {
            throw new IllegalStateException("error logging failed");
        }

        @Override
        public void successBatch(long elapsedMs, List<ParseResult> results, int affected, EntitySession session) {
            throw new IllegalStateException("batch success logging failed");
        }

        @Override
        public void errorBatch(Throwable error, List<ParseResult> results, EntitySession session) {
            throw new IllegalStateException("batch error logging failed");
        }
    }

    private static final class FailingSelectionPrint extends LegacyPrint {
        private final AtomicInteger handleChecks = new AtomicInteger();

        @Override
        public boolean isHandle(ParseResult result) {
            handleChecks.incrementAndGet();
            throw new IllegalStateException("handler selection failed");
        }
    }

    private static class LegacyPrint implements LogPrint<String> {
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicInteger lastSuccessElapsedMs = new AtomicInteger();

        @Override
        public void success(long time, ParseResult request) {
            successes.incrementAndGet();
            lastSuccessElapsedMs.set(Math.toIntExact(time));
        }

        @Override
        public void error(Throwable e, ParseResult request) {
            errors.incrementAndGet();
        }
    }
}
