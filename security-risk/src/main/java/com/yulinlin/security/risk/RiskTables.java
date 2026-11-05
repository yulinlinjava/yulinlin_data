package com.yulinlin.security.risk;

import com.yulinlin.data.core.anno.*;
import lombok.Data;

/** Internal schema owners; fixed column names do not depend on the application's underscore setting. */
public final class RiskTables {
    private RiskTables() {}
    @JoinTable(value = "yulinlin_risk_event", autoSchema = true)
    @JoinIndex(fields = {"subjectKey", "occurredAt"})
    @JoinIndex(fields = "occurredAt")
    @Data public static class EventRow {
        @JoinMeta(primaryKey = true) @JoinField(textLength = 64) public String id;
        @JoinField(name = "subject_key", textLength = 64) public String subjectKey;
        @JoinField(name = "user_id", textLength = 128) public String userId;
        @JoinField(name = "behavior_type", textLength = 64) public String behaviorType;
        @JoinField(name = "resource_id", textLength = 256) public String resourceId;
        @JoinField(name = "occurred_at") public Long occurredAt;
        @JoinField(textType = TextTypeEnum.text) public String payload;
    }
    @JoinTable(value = "yulinlin_risk_state", autoSchema = true)
    @JoinIndex(fields = {"subjectKey", "updatedAt"})
    @JoinIndex(fields = "updatedAt")
    @Data public static class StateRow {
        @JoinMeta(primaryKey = true) @JoinField(textLength = 64) public String id;
        @JoinField(name = "subject_key", textLength = 64) public String subjectKey;
        public Long version;
        @JoinField(name = "updated_at") public Long updatedAt;
        @JoinField(name = "retain_until") public Long retainUntil;
        @JoinField(textType = TextTypeEnum.text) public String payload;
    }
    @JoinTable(value = "yulinlin_risk_minute", autoSchema = true)
    @JoinIndex(fields = {"streamId", "minuteStart"})
    @JoinIndex(fields = "minuteStart")
    @Data public static class MinuteRow {
        @JoinMeta(primaryKey = true) @JoinField(textLength = 64) public String id;
        @JoinField(name = "stream_id", textLength = 64) public String streamId;
        @JoinField(name = "minute_start") public Long minuteStart;
        public Long version;
        @JoinField(textType = TextTypeEnum.text) public String payload;
    }
}

