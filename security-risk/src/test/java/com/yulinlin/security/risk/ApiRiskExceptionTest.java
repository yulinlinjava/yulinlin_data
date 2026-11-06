package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiRiskExceptionTest {
    private static final BehaviorEvent EVENT = new BehaviorEvent("user", "video", "v123");

    @Test void preservesServerDetailsAndRoundsRetryAfterToSeconds() {
        var match = new Match("continuous", 0, 120_000, 20, 2, 19, 18);
        var decision = new Decision(false, Status.BLOCKED, 161_001, List.of(match));
        var error = new ApiRiskException(EVENT, decision, 100_000);
        assertSame(EVENT, error.getEvent());
        assertSame(decision, error.getDecision());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals(62, error.getRetryAfterSeconds());
        assertEquals("62", error.getHeaders().getFirst("Retry-After"));
        assertFalse(error.getReason().contains("continuous"));
        assertEquals(60, new ApiRiskException(EVENT,
                new Decision(false, Status.BLOCKED, 160_000, List.of()), 100_000).getRetryAfterSeconds());
    }

    @Test void expiredRestrictionStillHasPositiveRetryAndAllowedDecisionCannotBeThrown() {
        var error = new ApiRiskException(EVENT, new Decision(false, Status.BLOCKED, 99, List.of()), 100);
        assertEquals(1, error.getRetryAfterSeconds());
        assertThrows(IllegalArgumentException.class, () -> new ApiRiskException(EVENT,
                new Decision(true, Status.SUSPECTED, 0, List.of()), 100));
    }
}
