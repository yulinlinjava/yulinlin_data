package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.Subject;

/** Internal snapshot contract for the two default detectors. Custom detectors own their recovery. */
interface PersistedRiskDetector extends RiskDetector {
    String snapshot(Subject subject);
    void restore(Subject subject, String payload, long now);
}
