package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import java.util.List;
import java.util.Optional;

/** Business-owned restriction policy and authoritative state. Calls are synchronous and may be retried. */
public interface RiskControl {
    /** Return only currently effective restrictions, including restrictions created outside this process. */
    Optional<Restriction> findRestriction(Subject subject);
    /** One aggregated call per hit event. Make persistence/idempotency safe across instances and retries.
     * Return empty when business policy elects not to restrict. */
    Optional<Restriction> onRisk(Subject subject, List<Match> hits);
    void release(Subject subject);
}
