package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface RiskService {
    /** Trusted server-side entry, before the business transaction. No database IO on the calling thread. */
    Decision checkAndRecord(BehaviorEvent event);
    Optional<View> inspect(Subject subject);
    List<View> listRisks(int offset, int limit);
    void release(Subject subject);
    void exempt(Subject subject, Duration duration);
    List<Event> history(Subject subject, long from, long to, int offset, int limit);
    Metrics metrics();
}

