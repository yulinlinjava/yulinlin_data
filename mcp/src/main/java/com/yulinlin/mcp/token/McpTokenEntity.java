package com.yulinlin.mcp.token;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.*;

/** 令牌表。只存 secret 的 SHA-256，不存明文。 */
@JoinTable(value = "yulinlin_mcp_token", autoSchema = true)
@JoinIndex(fields = {"keyId"}, unique = true)
@JoinIndex(fields = {"applicationId", "ownerId"})
@JoinIndex(fields = {"applicationId", "updatedAt"})
public class McpTokenEntity extends IdEntity<McpTokenEntity> {
    @JoinField(textLength = 64) private String applicationId;
    @JoinField(textLength = 16) private String keyId;
    @JoinField(textLength = 64) private String name;
    @JoinField(textLength = 255) private String remark;
    @JoinField(textLength = 512) private String ownerId;
    @JoinField(textLength = 512) private String issuerId;
    @JoinField(textLength = 16) private String status;
    @JoinField private Long expiresAt;
    @JoinField(textType = TextTypeEnum.text) private String scope;
    @JoinField(textType = TextTypeEnum.text) private String attributes;
    @JoinField(textType = TextTypeEnum.text) private String allowedIps;
    @JoinField private Integer rateLimit;
    @JoinField(textLength = 64) private String tokenHash;
    @JoinField(textLength = 64) private String previousHash;
    @JoinField private Long previousExpiresAt;
    @JoinField private Long createdAt;
    @JoinField private Long updatedAt;
    @JoinField private Long rotatedAt;
    @JoinField private Long lastUsedAt;
    @JoinField(textLength = 64) private String lastUsedIp;
    @JoinField private Long version;

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { applicationId = v; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String v) { keyId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { remark = v; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { ownerId = v; }
    public String getIssuerId() { return issuerId; }
    public void setIssuerId(String v) { issuerId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long v) { expiresAt = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { scope = v; }
    public String getAttributes() { return attributes; }
    public void setAttributes(String v) { attributes = v; }
    public String getAllowedIps() { return allowedIps; }
    public void setAllowedIps(String v) { allowedIps = v; }
    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer v) { rateLimit = v; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String v) { tokenHash = v; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String v) { previousHash = v; }
    public Long getPreviousExpiresAt() { return previousExpiresAt; }
    public void setPreviousExpiresAt(Long v) { previousExpiresAt = v; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long v) { createdAt = v; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long v) { updatedAt = v; }
    public Long getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Long v) { rotatedAt = v; }
    public Long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Long v) { lastUsedAt = v; }
    public String getLastUsedIp() { return lastUsedIp; }
    public void setLastUsedIp(String v) { lastUsedIp = v; }
    public Long getVersion() { return version; }
    public void setVersion(Long v) { version = v; }

    @Override public String toString() { return "McpTokenEntity[redacted]"; }
}
