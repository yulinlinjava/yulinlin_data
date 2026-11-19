package com.yulinlin.monitor;

import java.util.List;

/** Rejected before execution. Applications choose their own HTTP status and user-facing message. */
public final class SqlRiskException extends RuntimeException {
    private final List<String> reasons;
    private final boolean inspectionFailed;

    public SqlRiskException(List<String> reasons, boolean inspectionFailed, Throwable cause) {
        super(inspectionFailed ? "SQL 安全检查未完成，已拒绝执行" : "SQL 命中风险规则，已拒绝执行", cause);
        this.reasons = List.copyOf(reasons);
        this.inspectionFailed = inspectionFailed;
    }

    public List<String> getReasons() { return reasons; }
    public boolean isInspectionFailed() { return inspectionFailed; }
}
