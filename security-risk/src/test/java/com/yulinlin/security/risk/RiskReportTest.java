package com.yulinlin.security.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RiskReportTest {
    private static final Subject SUBJECT = new Subject("u", "video");
    private static ReplayEvent e(String resource, long at) { return new ReplayEvent("u", "video", resource, at); }
    private static RiskProperties config() { return RiskServiceTest.config(); }

    @Test void actualRuleWindowsAndDisplayCountersUseTheirOwnDenominators() throws Exception {
        var events = new ArrayList<ReplayEvent>();
        for (int i = 0; i <= 300; i++) events.add(e("v" + i, i * 1000L));
        var report = new RiskReplay(config()).report(SUBJECT, 0, 400_000, events);
        assertEquals(301, report.summary().requestCount());
        assertEquals(300, report.switches().differentResourceSwitches());
        assertEquals(300, report.switches().rapid().count());
        assertEquals(1d, report.switches().rapid().ratio());
        assertEquals(300, report.switches().rapid().longestRun());
        assertEquals(6, report.activity().activeMinutes());
        assertEquals(6, report.activity().longestConsecutiveMinutes());
        assertEquals(60, report.activity().peakMinuteRequests());
        assertEquals(300, report.activity().peakFiveMinuteRequests());
        assertEquals(300_000, report.activity().longestVisit().durationMillis());
        var rapid = report.hits().stream().filter(h -> h.ruleId().equals("rapid")).findFirst().orElseThrow();
        assertEquals(50_000, rapid.firstMatchedAt());
        assertEquals(51, rapid.fastSwitch().distinctVideos());
        assertEquals(50, rapid.fastSwitch().fastSwitchCount());
        assertEquals(50, rapid.fastSwitch().distinctFastVideos());
        assertEquals(1d, rapid.fastSwitch().fastSwitchRatio());
        assertEquals(50d / 51, rapid.fastSwitch().fastVideoRatio());
        assertNull(report.rules().rapid().minRatio());
        assertFalse(report.rules().rapid().gapInclusive());
        assertEquals(.7, report.rules().normal().minRatio());
        assertTrue(rapid.endInclusive());
        assertEquals(251, rapid.matchedRequestCount());
        assertNull(report.bestActiveWindow());
        var mapper = new ObjectMapper().findAndRegisterModules();
        assertEquals(report, mapper.readValue(mapper.writeValueAsString(report), RiskReport.class));
        assertThrows(UnsupportedOperationException.class, () -> report.minutes().clear());
    }

    @Test void gapsRepeatedResourcesAndSameTimestampRequestsAreExplicit() {
        var p = config(); p.getBehaviors().get("video").getFastSwitch().getRapid().setMinVideos(2);
        var events = List.of(e("a", 0), e("b", 1000), e("a", 2000), e("b", 4000), e("c", 9000),
                e("c", 10000), e(null, 11000), e("d", 12000), e("e", 12000), e("f", 18000));
        var report = new RiskReplay(p).report(SUBJECT, 0, 20000, events);
        assertEquals(6, report.switches().differentResourceSwitches());
        assertEquals(1, report.switches().sameResourceTransitions());
        assertEquals(1, report.switches().sameTimestampSwitches());
        assertEquals(3, report.switches().rapid().count());
        assertEquals(.5, report.switches().rapid().ratio());
        assertEquals(5, report.switches().normal().count());
        assertEquals(2, report.switches().rapid().longestRun());
        assertEquals(1, report.summary().missingResourceCount());
        assertEquals(2, report.hits().getFirst().fastSwitch().distinctFastVideos());
        assertEquals(2, report.hits().getFirst().fastSwitch().distinctVideos());
    }

    @Test void firstHitEvidenceExcludesLaterRequestsWithTheSameTimestamp() {
        var events = new ArrayList<ReplayEvent>();
        for (int i = 0; i < 210; i++) events.add(e("v" + i, 1000));
        var report = new RiskReplay(config()).report(SUBJECT, 0, 2000, events);
        var rapid = report.hits().stream().filter(h -> h.ruleId().equals("rapid")).findFirst().orElseThrow();
        var normal = report.hits().stream().filter(h -> h.ruleId().equals("normal")).findFirst().orElseThrow();
        assertEquals(51, rapid.fastSwitch().requestCount());
        assertEquals(50, rapid.fastSwitch().distinctFastVideos());
        assertEquals(50, rapid.fastSwitch().sameTimestampSwitches());
        assertEquals(200, normal.fastSwitch().requestCount());
        assertEquals(199, normal.fastSwitch().distinctFastVideos());
        assertEquals(210, report.summary().requestCount());
    }

    @Test void duplicateIdsAreCountedOnceAndConflictingCopiesAreVisible() {
        var first = new ReplayEvent("u", "video", "a", "same", 1000);
        var input = new ArrayList<>(List.of(new ReplayEvent("u", "video", "b", "same", 2000), first, first, e("c", 3000)));
        var original = List.copyOf(input);
        var report = new RiskReplay(config()).report(SUBJECT, 0, 4000, input);
        assertEquals(4, report.summary().inputCount());
        assertEquals(2, report.summary().requestCount());
        assertEquals(2, report.summary().duplicateCount());
        assertEquals(1, report.summary().conflictingDuplicateCount());
        assertEquals(original, input);
        assertEquals(0, report.switches().rapid().count()); // Exactly two seconds is not rapid.
    }

    @Test void activityPeaksUseFiveCalendarMinutesAndSegmentsBreakOnlyAboveFiveMinutes() {
        var events = List.of(e("a", 10_000), e("b", 20_000), e("c", 70_000), e("d", 190_000),
                e("e", 250_000), e("f", 310_000), e("g", 671_000));
        var activity = new RiskReplay(config()).report(SUBJECT, 0, 700_000, events).activity();
        assertEquals(6, activity.activeMinutes());
        assertEquals(3, activity.longestConsecutiveMinutes());
        assertEquals(2, activity.peakMinuteRequests());
        assertEquals(5, activity.peakFiveMinuteRequests());
        assertEquals(300_000, activity.longestVisit().durationMillis());
        assertEquals(6, activity.longestVisit().requestCount());
        assertEquals(361_000, activity.longestGapMillis());
        var boundary = new RiskReplay(config()).report(SUBJECT, 0, 600_002,
                List.of(e("a", 0), e("b", 300_000), e("c", 600_001)));
        assertEquals(300_000, boundary.activity().longestVisit().durationMillis());
        assertEquals(2, boundary.activity().longestVisit().requestCount());
    }

    @Test void sixHourCoverageIsNotSixHoursOfContinuousRequestsAndCurrentBucketIsExcluded() {
        var events = new ArrayList<ReplayEvent>();
        for (int i = 0; i < 36; i++) { events.add(e("v" + i, i * 600_000L)); events.add(e("v" + i, i * 600_000L + 1000)); }
        events.add(e("last", 21_600_000));
        var report = new RiskReplay(config()).report(SUBJECT, 0, 21_600_001, events);
        var hit = report.hits().getFirst();
        assertEquals("active-access", hit.componentId());
        assertEquals(72, hit.activeAccess().requestCount());
        assertEquals(36, hit.activeAccess().activeBuckets());
        assertEquals(36, hit.activeAccess().totalBuckets());
        assertEquals(36, hit.activeAccess().activeMinutes());
        assertEquals(1d, hit.activeAccess().activeRatio());
        assertEquals(599_000, hit.activeAccess().longestGapMillis());
        assertFalse(hit.endInclusive());
        assertEquals(1000, report.activity().longestVisit().durationMillis());
        assertEquals(hit.activeAccess(), report.bestActiveWindow());
        assertEquals(36, report.bestActiveWindow().buckets().size());
    }

    @Test void emptyCapacityAndDisabledReportsDoNotClaimNormality() {
        var p = config();
        var empty = new RiskReplay(p).report(SUBJECT, 0, 1000, List.of());
        assertEquals(RiskReport.Status.NO_DATA, empty.status());
        assertNull(empty.switches().rapid().ratio());
        assertNull(empty.activity().longestVisit().start());
        p.getMemory().setMaximumEventsPerSubject(200);
        var events = new ArrayList<ReplayEvent>();
        for (int i = 0; i < 201; i++) events.add(e("same", i * 1000L));
        var full = new RiskReplay(p).report(SUBJECT, 0, 300_000, events);
        assertTrue(full.incomplete());
        assertEquals(RiskReport.Status.INCOMPLETE, full.status());
        p.getBehaviors().get("video").setEnabled(false);
        var disabled = new RiskReplay(p).report(SUBJECT, 0, 1000, List.of(e("a", 0)));
        assertEquals(RiskReport.Status.DISABLED, disabled.status());
        assertNull(disabled.rules().rapid());
    }

    @Test void customComponentsAndIndependentRunsArePreserved() {
        var p = config(); p.getBehaviors().get("video").setComponents(List.of("custom"));
        var created = new AtomicInteger();
        var replay = new RiskReplay(p, () -> { created.incrementAndGet(); return List.of(new RiskServiceTest.CountingDetector("custom", true)); });
        var a = replay.report(SUBJECT, 0, 1000, List.of(e("a", 0)));
        var b = replay.report(SUBJECT, 0, 1000, List.of(e("a", 0)));
        assertEquals(a, b); assertEquals(2, created.get());
        assertEquals("custom", a.hits().getFirst().componentId());
        assertNull(a.hits().getFirst().fastSwitch()); assertNull(a.hits().getFirst().activeAccess());
        assertNull(a.hits().getFirst().endInclusive());
        assertEquals(List.of("custom"), a.rules().customComponents());
    }

    @Test void explicitRangeSubjectAndTimezoneAreValidated() {
        var replay = new RiskReplay(config());
        assertThrows(IllegalArgumentException.class, () -> replay.report(SUBJECT, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> replay.report(SUBJECT, 0, 1000, List.of(e("a", 1000))));
        assertThrows(IllegalArgumentException.class, () -> replay.report(SUBJECT, 0, 1000, List.of(new ReplayEvent("other", "video", "a", 0))));
        long base = java.time.Instant.parse("2026-11-08T00:00:00Z").toEpochMilli();
        var report = replay.report(SUBJECT, base, base + 3_600_001, ZoneId.of("Asia/Kathmandu"), List.of(e("a", base), e("b", base + 3_600_000)));
        assertEquals("Asia/Kathmandu", report.zoneId());
        assertEquals(base - 2_700_000, report.hours().getFirst().start());
        assertEquals(2, report.hours().size());
    }

    @Test void hourlyBucketsDoNotOverlapAcrossHalfHourDaylightSavingJump() {
        long from = java.time.Instant.parse("2026-10-03T15:30:00Z").toEpochMilli();
        var report = new RiskReplay(config()).report(SUBJECT, from, from + 3_600_001,
                ZoneId.of("Australia/Lord_Howe"), List.of(e("a", from + 900_000), e("b", from + 2_700_000)));
        assertEquals(2, report.hours().size());
        assertEquals(from, report.hours().getFirst().start());
        assertEquals(from + 1_800_000, report.hours().getFirst().end());
        assertEquals(report.hours().getFirst().end(), report.hours().getLast().start());
    }
}
