package com.yulinlin.mcp.store;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.*;

/** Local MCP credentials (hashes only), settings, tasks and audit records. */
@JoinTable(value = "yulinlin_mcp_record", autoSchema = true)
@JoinIndex(fields = {"recordKey"}, unique = true)
@JoinIndex(fields = {"kind", "updatedAt"})
public class McpRecordEntity extends IdEntity<McpRecordEntity> {
    @JoinField(textLength = 160) @JoinWhere private String recordKey;
    @JoinField(textLength = 96) private String kind;
    @JoinField(textType = TextTypeEnum.text) private String payload;
    @JoinField private Long updatedAt;
    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String value) { recordKey = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long value) { updatedAt = value; }
    @Override public String toString() { return "McpRecord[redacted]"; }
}
