package com.yulinlin.monitor;

import java.util.List;

/** A pre-execution decision, not a database execution result. */
public record SqlRiskRecord(long timestamp, String group, String sql,
                            boolean blocked, boolean inspectionFailed, List<String> reasons) {
    public SqlRiskRecord { reasons = List.copyOf(reasons); }
}
