package com.yulinlin.mcp.token;

import com.yulinlin.mcp.auth.McpScope;
import java.util.*;

/**
 * 令牌的存储模型。时间均为毫秒时间戳，0 表示无/不限；字符串空值统一为 ""。
 * 修改请用 {@link #toBuilder()}，避免手写全参构造。
 */
public record McpToken(
        String id,
        String keyId,
        String name,
        String remark,
        String ownerId,
        String issuerId,
        McpTokenStatus status,
        long expiresAt,
        List<String> scope,
        Map<String, String> attributes,
        List<String> allowedIps,
        int rateLimit,
        String tokenHash,
        String previousHash,
        long previousExpiresAt,
        long createdAt,
        long updatedAt,
        long rotatedAt,
        long lastUsedAt,
        String lastUsedIp,
        long version) {

    public McpToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(status, "status");
        if (status == McpTokenStatus.EXPIRED) throw new IllegalArgumentException("EXPIRED 是计算状态，不能存储");
        name = name == null ? "" : name;
        remark = remark == null ? "" : remark;
        issuerId = issuerId == null ? "" : issuerId;
        scope = McpScope.normalize(scope);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        allowedIps = allowedIps == null ? List.of() : List.copyOf(allowedIps);
        tokenHash = tokenHash == null ? "" : tokenHash;
        previousHash = previousHash == null ? "" : previousHash;
        lastUsedIp = lastUsedIp == null ? "" : lastUsedIp;
    }

    public boolean expired(long now) { return expiresAt > 0 && expiresAt <= now; }

    /** 对外展示的状态：ACTIVE 但已过期时返回 EXPIRED。 */
    public McpTokenStatus effectiveStatus(long now) {
        return status == McpTokenStatus.ACTIVE && expired(now) ? McpTokenStatus.EXPIRED : status;
    }

    public boolean usable(long now) { return effectiveStatus(now) == McpTokenStatus.ACTIVE; }

    /** 校验 secret 的 hash：当前密钥，或轮换宽限期内的旧密钥。 */
    public boolean matches(String secretHash, long now) {
        if (McpTokenCodec.sameHash(tokenHash, secretHash)) return true;
        return previousExpiresAt > now && McpTokenCodec.sameHash(previousHash, secretHash);
    }

    @Override public String toString() { return "McpToken[" + keyId + "," + status + "]"; }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, keyId, name, remark, ownerId, issuerId, tokenHash, previousHash, lastUsedIp;
        private McpTokenStatus status = McpTokenStatus.ACTIVE;
        private long expiresAt, previousExpiresAt, createdAt, updatedAt, rotatedAt, lastUsedAt, version;
        private List<String> scope, allowedIps;
        private Map<String, String> attributes;
        private int rateLimit;

        private Builder() { }

        private Builder(McpToken t) {
            id = t.id; keyId = t.keyId; name = t.name; remark = t.remark; ownerId = t.ownerId; issuerId = t.issuerId;
            status = t.status; expiresAt = t.expiresAt; scope = t.scope; attributes = t.attributes; allowedIps = t.allowedIps;
            rateLimit = t.rateLimit; tokenHash = t.tokenHash; previousHash = t.previousHash; previousExpiresAt = t.previousExpiresAt;
            createdAt = t.createdAt; updatedAt = t.updatedAt; rotatedAt = t.rotatedAt; lastUsedAt = t.lastUsedAt;
            lastUsedIp = t.lastUsedIp; version = t.version;
        }

        public Builder id(String v) { id = v; return this; }
        public Builder keyId(String v) { keyId = v; return this; }
        public Builder name(String v) { name = v; return this; }
        public Builder remark(String v) { remark = v; return this; }
        public Builder ownerId(String v) { ownerId = v; return this; }
        public Builder issuerId(String v) { issuerId = v; return this; }
        public Builder status(McpTokenStatus v) { status = v; return this; }
        public Builder expiresAt(long v) { expiresAt = v; return this; }
        public Builder scope(List<String> v) { scope = v; return this; }
        public Builder attributes(Map<String, String> v) { attributes = v; return this; }
        public Builder allowedIps(List<String> v) { allowedIps = v; return this; }
        public Builder rateLimit(int v) { rateLimit = v; return this; }
        public Builder tokenHash(String v) { tokenHash = v; return this; }
        public Builder previousHash(String v) { previousHash = v; return this; }
        public Builder previousExpiresAt(long v) { previousExpiresAt = v; return this; }
        public Builder createdAt(long v) { createdAt = v; return this; }
        public Builder updatedAt(long v) { updatedAt = v; return this; }
        public Builder rotatedAt(long v) { rotatedAt = v; return this; }
        public Builder lastUsedAt(long v) { lastUsedAt = v; return this; }
        public Builder lastUsedIp(String v) { lastUsedIp = v; return this; }
        public Builder version(long v) { version = v; return this; }

        public McpToken build() {
            return new McpToken(id, keyId, name, remark, ownerId, issuerId, status, expiresAt, scope, attributes, allowedIps,
                    rateLimit, tokenHash, previousHash, previousExpiresAt, createdAt, updatedAt, rotatedAt, lastUsedAt,
                    lastUsedIp, version);
        }
    }
}
