package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;

/** Same-subject calls are serialized by RiskService; different subjects may run concurrently. */
public interface RiskDetector {
    String id();
    Detection detect(BehaviorEvent event, long occurredAt);
    /** Called on restriction, release or exemption. Must be idempotent. */
    void reset(Subject subject);

    /** Local service retirement/close. Shared-state implementations may override this to leave
     * their global data intact and manage its retention themselves. */
    default void evict(Subject subject) { reset(subject); }
}
