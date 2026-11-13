package com.yulinlin.admin.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.security.risk.RiskModels.ReplayEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalRiskAnalysisTest {
    @TempDir Path directory;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 8, 0, 0);
    private static final long AT = START.atZone(ZONE).toInstant().toEpochMilli();

    @Test
    void reportsSuspectsPerUserWithoutCrossUserCapacityOrCounterContamination() throws Exception {
        var properties = AdminRiskReplayTest.config();
        properties.getMemory().setMaximumSize(1); // A whole-site report must still independently analyze every user.
        var events = new ArrayList<ReplayEvent>();
        for (int i = 0; i < 51; i++) for (String user : List.of("bot-a", "bot-b"))
            events.add(new ReplayEvent(user, "video-play-url", "v" + i, "e" + i, AT + i * 1000L));
        events.add(new ReplayEvent("viewer", "video-play-url", "v1", AT));
        events.add(new ReplayEvent("viewer", "video-play-url", "v2", AT + 120_000));
        var loaded = new LocalRiskHistory.Loaded(new LocalRiskHistory.Range(START, START.plusHours(12), ZONE), events);
        var report = LocalRiskAnalysis.analyze(loaded, properties);
        assertEquals(104, report.records());
        assertEquals(3, report.users());
        assertEquals(2, report.suspectedUsers());
        assertEquals(0, report.incompleteUsers());
        assertEquals(LocalRiskAnalysis.Finding.OBSERVING, report.results().getLast().finding());
        for (var user : report.results().subList(0, 2)) {
            assertEquals(AT + 50_000, user.firstHitAt());
            assertEquals(50, user.hits().getFirst().statistics().get("fastVideos"));
        }
        var files = LocalRiskAnalysis.write(report, directory);
        var json = new ObjectMapper().readTree(files.json().toFile());
        assertEquals(3, json.get("results").size());
        assertEquals(2, json.get("suspectedUsers").asInt());
        assertTrue(Files.readString(files.markdown()).contains("bot-b"));
        assertTrue(Files.readString(files.markdown()).contains("观察不足"));
    }

    @Test
    void incompleteAndEmptySamplesAreNotReportedAsNormalUsers() {
        var properties = AdminRiskReplayTest.config();
        properties.getMemory().setMaximumEventsPerSubject(200);
        var events = new ArrayList<ReplayEvent>();
        for (int i = 0; i < 201; i++)
            events.add(new ReplayEvent("retries", "video-play-url", "same-video", AT + i * 1000L));
        var range = new LocalRiskHistory.Range(START, START.plusHours(12), ZONE);
        var report = LocalRiskAnalysis.analyze(new LocalRiskHistory.Loaded(range, events), properties);
        assertEquals(LocalRiskAnalysis.Finding.INCOMPLETE, report.results().getFirst().finding());
        assertEquals(0, report.suspectedUsers());
        assertEquals(1, report.incompleteUsers());
        var empty = LocalRiskAnalysis.analyze(new LocalRiskHistory.Loaded(range, List.of()), properties);
        assertEquals(0, empty.users());
        assertEquals(0, empty.records());
    }

    @Test
    void onlyMatchedUsersStrictlyAboveRequestThresholdAppearInBothReports() throws Exception {
        var events = new ArrayList<ReplayEvent>();
        for (int i = 0; i < 301; i++) {
            if (i < 300) events.add(new ReplayEvent("exactly-300", "video-play-url", "v" + i, AT + i * 1000L));
            events.add(new ReplayEvent("above-300", "video-play-url", "v" + i, AT + i * 1000L));
            events.add(new ReplayEvent("no-hit-301", "video-play-url", "same-video", AT + i * 1000L));
        }
        var report = LocalRiskAnalysis.analyze(new LocalRiskHistory.Loaded(
                new LocalRiskHistory.Range(START, START.plusHours(12), ZONE), events), AdminRiskReplayTest.config());
        assertEquals(3, report.users());
        assertEquals(2, report.suspectedUsers());
        var files = LocalRiskAnalysis.write(report, directory, 300);
        var json = new ObjectMapper().readTree(files.json().toFile());
        assertEquals(300, json.get("reportRequestsGreaterThan").asInt());
        assertEquals(1, json.get("listedUsers").asInt());
        assertEquals("above-300", json.get("results").get(0).get("userId").asText());
        assertEquals(1, json.get("results").size());
        var markdown = Files.readString(files.markdown());
        assertTrue(markdown.contains("above-300"));
        assertFalse(markdown.contains("exactly-300"));
        assertFalse(markdown.contains("no-hit-301"));
    }
}
