package com.yulinlin.monitor;

import java.util.List;
import java.util.Objects;

public record SqlRiskResult(boolean allowed, List<String> reasons) {
    public SqlRiskResult {
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (allowed != reasons.isEmpty() || reasons.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("允许结果不能包含风险原因，拒绝结果必须包含非空原因");
    }

    public static SqlRiskResult allow() { return new SqlRiskResult(true, List.of()); }
    public static SqlRiskResult reject(String reason) { return new SqlRiskResult(false, List.of(reason)); }
}
