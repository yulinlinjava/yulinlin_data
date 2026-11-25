package com.yulinlin.mcp.process;

import com.yulinlin.mcp.McpException;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

/** Inert handles only: these tests never signal an operating-system process. */
class ProcessControlServiceTest {
    private static final Instant START = Instant.parse("2026-10-30T01:02:03.123456Z");
    private final FakeProcess self = new FakeProcess(500), target = new FakeProcess(600);
    private final ProcessControlService service = new ProcessControlService(self,
            pid -> pid == target.pid() ? Optional.of(target) : Optional.empty());

    @Test void normalStopIsOneRequestAndDoesNotEscalateOrTraverseChildren() {
        var result = service.stop(600, START.toString(), false, true);
        assertThat(result.state()).isEqualTo("requested");
        assertThat(target.normalCalls).isEqualTo(1); assertThat(target.forceCalls).isZero();
        assertThat(target.childrenCalls).isZero();
    }
    @Test void explicitForceAndObservedExitAreReportedSeparately() {
        target.exitOnStop = true;
        var result = service.stop(600, START.toString(), true, true);
        assertThat(result.state()).isEqualTo("exited"); assertThat(result.force()).isTrue();
        assertThat(target.normalCalls).isZero(); assertThat(target.forceCalls).isEqualTo(1);
    }
    @Test void currentProcessInitAndParentsAreProtectedEvenForForce() {
        self.parent = new FakeProcess(400); self.parent.parent = new FakeProcess(1);
        for (long pid : new long[]{1,400,500}) {
            assertThatThrownBy(() -> service.stop(pid, START.toString(), true, true))
                    .isInstanceOfSatisfying(McpException.class, error -> assertThat(error.status()).isEqualTo(403));
        }
        assertThat(target.normalCalls + target.forceCalls).isZero();
    }
    @Test void pidReuseOrUnreadableIdentityNeverSendsASignal() {
        // Different start instants within the same millisecond must not compare equal.
        assertThatThrownBy(() -> service.stop(600, START.plusNanos(1).toString(), false, true))
                .isInstanceOfSatisfying(McpException.class, error -> assertThat(error.status()).isEqualTo(409));
        target.started = null;
        assertThatThrownBy(() -> service.stop(600, START.toString(), true, true)).isInstanceOf(McpException.class);
        assertThat(target.normalCalls + target.forceCalls).isZero();
    }
    @Test void malformedOrUnconfirmedRequestsNeverSendASignal() {
        assertThatThrownBy(() -> service.stop(0, START.toString(), false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stop(600, START.toString(), false, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stop(600, "invalid", false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stop(600, null, false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(target.normalCalls + target.forceCalls).isZero();
    }
    @Test void exitedProcessesAreIdempotentAndPermissionsDoNotTriggerForceFallback() {
        assertThat(service.stop(700, START.toString(), false, true).state()).isEqualTo("exited");
        target.alive = false;
        assertThat(service.stop(600, START.toString(), true, true).state()).isEqualTo("exited");
        assertThat(target.forceCalls).isZero(); target.alive = true; target.accept = false;
        assertThatThrownBy(() -> service.stop(600, START.toString(), false, true))
                .isInstanceOfSatisfying(McpException.class, error -> assertThat(error.status()).isEqualTo(403));
        assertThat(target.normalCalls).isEqualTo(1); assertThat(target.forceCalls).isZero();
    }
    @Test void unsupportedNormalTerminationRequiresExplicitForce() {
        target.normalSupported = false;
        assertThatThrownBy(() -> service.stop(600, START.toString(), false, true)).isInstanceOf(McpException.class);
        assertThat(target.normalCalls + target.forceCalls).isZero();
        service.stop(600, START.toString(), true, true); assertThat(target.forceCalls).isEqualTo(1);
    }

    static class FakeProcess implements ProcessHandle {
        final long pid;
        Instant started = START;
        FakeProcess parent;
        boolean alive = true, accept = true, exitOnStop, normalSupported = true;
        int normalCalls, forceCalls, childrenCalls;
        FakeProcess(long pid) { this.pid = pid; }
        public long pid() { return pid; }
        public Optional<ProcessHandle> parent() { return Optional.ofNullable(parent); }
        public Stream<ProcessHandle> children() { childrenCalls++; return Stream.empty(); }
        public Stream<ProcessHandle> descendants() { childrenCalls++; return Stream.empty(); }
        public boolean isAlive() { return alive; }
        public boolean supportsNormalTermination() { return normalSupported; }
        public boolean destroy() { normalCalls++; if (accept && exitOnStop) alive = false; return accept; }
        public boolean destroyForcibly() { forceCalls++; if (accept && exitOnStop) alive = false; return accept; }
        public CompletableFuture<ProcessHandle> onExit() { throw new AssertionError("No waiting or background polling"); }
        public int compareTo(ProcessHandle other) { return Long.compare(pid, other.pid()); }
        public Info info() {
            return new Info() {
                public Optional<String> command() { return Optional.empty(); }
                public Optional<String> commandLine() { return Optional.empty(); }
                public Optional<String[]> arguments() { return Optional.empty(); }
                public Optional<Instant> startInstant() { return Optional.ofNullable(started); }
                public Optional<Duration> totalCpuDuration() { return Optional.empty(); }
                public Optional<String> user() { return Optional.empty(); }
            };
        }
    }
}


