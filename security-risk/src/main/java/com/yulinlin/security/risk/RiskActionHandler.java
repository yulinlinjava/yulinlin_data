package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;

/** Notification on entering a new risk state. Must be nonblocking; not invoked again for every denied request. */
@FunctionalInterface
public interface RiskActionHandler {
    void onRisk(BehaviorEvent event, Decision decision);
}

