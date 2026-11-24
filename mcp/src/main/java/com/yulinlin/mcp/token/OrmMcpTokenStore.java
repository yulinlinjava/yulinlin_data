package com.yulinlin.mcp.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.common.model.*;
import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.mcp.McpProperties;
import java.util.*;

/** 默认令牌存储：yulinlin_mcp_token 表，version 乐观锁。 */
public class OrmMcpTokenStore implements McpTokenStore {
    private static final TypeReference<List<String>> LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() { };
    private final ObjectMapper json;
    private final McpProperties properties;
    private String group;

    public OrmMcpTokenStore(ObjectMapper json, McpProperties properties) {
        this.json = json;
        this.properties = properties;
    }

    @Override
    public void initialize() {
        if ((properties.getGroup() == null || properties.getGroup().isBlank()) && SessionUtil.route().sessionSize() != 1)
            throw new IllegalStateException("多个数据源时必须指定 yulinlin.mcp.group");
        var session = SessionUtil.route().session(properties.getGroup());
        group = session.group();
        session.initializeSchema(List.of(McpTokenEntity.class));
    }

    private String app() { return properties.getApplicationId(); }

    private ModelSelectWrapper<McpTokenEntity> select() {
        var q = ModelSelectWrapper.newInstance(group, McpTokenEntity.class).cache(false).autoUpdate(false);
        q.getRequest().setCluster(JoinCluster.master);
        return q.eq("applicationId", app());
    }

    @Override
    public McpToken findById(String id) {
        if (id == null || id.isBlank()) return null;
        var row = select().eq("id", id).selectOne();
        return row == null ? null : decode(row);
    }

    @Override
    public McpToken findByKeyId(String keyId) {
        if (!McpTokenCodec.isKeyId(keyId)) return null;
        var row = select().eq("keyId", keyId).selectOne();
        return row == null ? null : decode(row);
    }

    @Override
    public List<McpToken> list(McpTokenQuery query) {
        return where(select(), query).orderBy("createdAt", false).orderBy("id", false)
                .page(query.page(), query.size()).selectList().stream().map(this::decode).toList();
    }

    @Override
    public long count(McpTokenQuery query) { return where(select(), query).count(); }

    @Override
    public long countUnrevoked(String ownerId) {
        return select().eq("ownerId", ownerId).ne("status", McpTokenStatus.REVOKED.name()).count();
    }

    private ModelSelectWrapper<McpTokenEntity> where(ModelSelectWrapper<McpTokenEntity> q, McpTokenQuery query) {
        if (query.ownerId() != null) q.eq("ownerId", query.ownerId());
        if (query.keyword() != null) {
            String keyword = query.keyword();
            q.and(w -> w.or(or -> or.like("name", keyword).eq("keyId", keyword)));
        }
        if (query.status() != null) {
            long now = query.now();
            switch (query.status()) {
                case ACTIVE -> q.eq("status", McpTokenStatus.ACTIVE.name())
                        .and(w -> w.or(or -> or.eq("expiresAt", 0L).gt("expiresAt", now)));
                case EXPIRED -> q.eq("status", McpTokenStatus.ACTIVE.name()).gt("expiresAt", 0L).lte("expiresAt", now);
                default -> q.eq("status", query.status().name());
            }
        }
        return q;
    }

    @Override
    public void insert(McpToken token) {
        var row = encode(token);
        row.setLastUsedAt(token.lastUsedAt());
        row.setLastUsedIp(token.lastUsedIp());
        if (ModelInsertWrapper.newInstance(group, row).execute() != 1) throw new IllegalStateException("MCP 令牌保存失败");
    }

    @Override
    public boolean update(McpToken token, long expectedVersion) {
        var row = encode(token);
        int changed = ModelUpdateWrapper.newInstance(group, McpTokenEntity.class)
                .field("name", row.getName())
                .field("remark", row.getRemark())
                .field("ownerId", row.getOwnerId())
                .field("issuerId", row.getIssuerId())
                .field("status", row.getStatus())
                .field("expiresAt", row.getExpiresAt())
                .field("scope", row.getScope())
                .field("attributes", row.getAttributes())
                .field("allowedIps", row.getAllowedIps())
                .field("rateLimit", row.getRateLimit())
                .field("tokenHash", row.getTokenHash())
                .field("previousHash", row.getPreviousHash())
                .field("previousExpiresAt", row.getPreviousExpiresAt())
                .field("updatedAt", row.getUpdatedAt())
                .field("rotatedAt", row.getRotatedAt())
                .field("version", row.getVersion())
                .eq("applicationId", app()).eq("id", token.id()).eq("version", expectedVersion)
                .execute();
        return changed == 1;
    }

    @Override
    public void markUsed(String id, long usedAt, String ip) {
        ModelUpdateWrapper.newInstance(group, McpTokenEntity.class)
                .field("lastUsedAt", usedAt).field("lastUsedIp", ip == null ? "" : ip)
                .eq("applicationId", app()).eq("id", id).lte("lastUsedAt", usedAt - 60_000L)
                .execute();
    }

    @Override
    public void delete(String id) {
        ModelDeleteWrapper.newInstance(group, McpTokenEntity.class).eq("applicationId", app()).eq("id", id).execute();
    }

    private McpTokenEntity encode(McpToken t) {
        var row = new McpTokenEntity();
        row.setId(t.id());
        row.setApplicationId(app());
        row.setKeyId(t.keyId());
        row.setName(t.name());
        row.setRemark(t.remark());
        row.setOwnerId(t.ownerId());
        row.setIssuerId(t.issuerId());
        row.setStatus(t.status().name());
        row.setExpiresAt(t.expiresAt());
        row.setScope(write(t.scope()));
        row.setAttributes(write(t.attributes()));
        row.setAllowedIps(write(t.allowedIps()));
        row.setRateLimit(t.rateLimit());
        row.setTokenHash(t.tokenHash());
        row.setPreviousHash(t.previousHash());
        row.setPreviousExpiresAt(t.previousExpiresAt());
        row.setCreatedAt(t.createdAt());
        row.setUpdatedAt(t.updatedAt());
        row.setRotatedAt(t.rotatedAt());
        row.setVersion(t.version());
        return row;
    }

    private McpToken decode(McpTokenEntity r) {
        // 范围缺失时拒绝而不是退化为 "*"，避免数据损坏导致权限放大
        if (r.getScope() == null || r.getScope().isBlank()) throw new IllegalStateException("MCP 令牌数据损坏: scope 为空");
        return McpToken.builder()
                .id(r.getId()).keyId(r.getKeyId()).name(r.getName()).remark(r.getRemark())
                .ownerId(r.getOwnerId()).issuerId(r.getIssuerId())
                .status(McpTokenStatus.valueOf(r.getStatus()))
                .expiresAt(n(r.getExpiresAt()))
                .scope(read(r.getScope(), LIST, null))
                .attributes(read(r.getAttributes(), MAP, Map.of()))
                .allowedIps(read(r.getAllowedIps(), LIST, List.of()))
                .rateLimit(r.getRateLimit() == null ? 0 : r.getRateLimit())
                .tokenHash(r.getTokenHash()).previousHash(r.getPreviousHash())
                .previousExpiresAt(n(r.getPreviousExpiresAt()))
                .createdAt(n(r.getCreatedAt())).updatedAt(n(r.getUpdatedAt())).rotatedAt(n(r.getRotatedAt()))
                .lastUsedAt(n(r.getLastUsedAt())).lastUsedIp(r.getLastUsedIp())
                .version(n(r.getVersion()))
                .build();
    }

    private static long n(Long value) { return value == null ? 0 : value; }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("MCP 令牌编码失败", e); }
    }

    private <T> T read(String value, TypeReference<T> type, T empty) {
        if (value == null || value.isBlank()) return empty;
        try { return json.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("MCP 令牌数据损坏", e); }
    }
}
