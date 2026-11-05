package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import java.util.List;
import java.util.Optional;

/** Pure in-memory rule extension. Never perform IO from evaluate(). */
public interface RiskRule {
    String type();
    Optional<Match> evaluate(RiskProperties.Rule rule, long windowEnd, List<Minute> minutes);

    static RiskRule activeMinutes() { return builtin("ACTIVE_MINUTES", false); }
    static RiskRule denseAccess() { return builtin("DENSE_ACCESS", true); }
    private static RiskRule builtin(String type, boolean dense) {
        return new RiskRule() {
            public String type() { return type; }
            public Optional<Match> evaluate(RiskProperties.Rule rule, long end, List<Minute> minutes) {
                long start = end - rule.getWindow().toMillis();
                long requests = 0, intervals = 0, shortIntervals = 0;
                int active = 0;
                for (Minute minute : minutes) {
                    if (minute.start() < start || minute.start() >= end) continue;
                    requests += minute.requests();
                    if (minute.requests() > 0) active++;
                    long n = minute.intervals();
                    long s = minute.shortIntervals().getOrDefault(rule.getId(), 0L);
                    // The first edge is outside the window; do not let an old pause dilute this window.
                    if (n > 0 && minute.firstPreviousAt() < start) {
                        n--;
                        // An outside-window edge may still be short; its exact inclusion is tracked separately.
                        s -= minute.shortIntervals().getOrDefault("first:" + rule.getId(), 0L);
                    }
                    intervals += n; shortIntervals += s;
                }
                if (active < rule.getMinActiveMinutes()) return Optional.empty();
                if (dense && (requests < rule.getMinRequests() || intervals == 0
                        || (double) shortIntervals / intervals < rule.getMinShortGapRatio())) return Optional.empty();
                return Optional.of(new Match(rule.getId(), start, end, requests, active, intervals, shortIntervals));
            }
        };
    }
}

