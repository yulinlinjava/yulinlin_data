package com.yulinlin.mcp.token;

import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.McpProperties;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.mcp.token.McpTokenEvent.Action;
import com.yulinlin.mcp.token.McpTokenView.Issued;
import com.yulinlin.mcp.tool.McpToolRegistry;
import com.yulinlin.security.auth.IUserDetails;
import java.time.Clock;
import java.util.*;
import java.util.function.*;

/**
 * 令牌管理：签发、查询、修改、轮换、禁用、启用、撤销、删除。
 * <p>
 * 权限规则：所有人只能管理自己的令牌；{@link McpAccessPolicy#canManage} 可以管理所有人的令牌、为他人签发、设置 attributes。
 * 范围只能收窄，不能超出所属账号自身的工具权限。
 */
public class McpTokenService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpTokenService.class);
    private static final long MINUTE = 60_000L;

    private final McpTokenStore store;
    private final McpIdentityProvider identities;
    private final McpAccessPolicy policy;
    private final Supplier<McpToolRegistry> tools;
    private final McpProperties.Token config;
    private final Consumer<McpTokenEvent> events;
    private final Clock clock;
    private final McpTokenCache cache;
    private final Map<String, Long> lastUseWrites = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) { return size() > 4096; }
    };

    /** 操作人及其来源 IP（用于事件和审计）。 */
    public record Actor(IUserDetails user, String ip) {
        public Actor { Objects.requireNonNull(user, "user"); }
        public static Actor of(IUserDetails user) { return new Actor(user, null); }
    }

    /** 签发参数。ownerId 为空表示自己；expiresAt 为 null/0 表示不过期。 */
    public record IssueRequest(String name, String remark, String ownerId, Long expiresAt, List<String> scope,
                               Map<String, String> attributes, List<String> allowedIps, Integer rateLimit) {
        public static IssueRequest of(String name) { return new IssueRequest(name, null, null, null, null, null, null, null); }
    }

    /** 修改参数，null 表示不修改；expiresAt=0 表示改为不过期，allowedIps=[] 表示取消限制。 */
    public record UpdateRequest(String name, String remark, Long expiresAt, List<String> scope,
                                Map<String, String> attributes, List<String> allowedIps, Integer rateLimit) { }

    public McpTokenService(McpTokenStore store, McpIdentityProvider identities, McpAccessPolicy policy,
                           Supplier<McpToolRegistry> tools, McpProperties.Token config,
                           Consumer<McpTokenEvent> events, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.identities = Objects.requireNonNull(identities);
        this.policy = Objects.requireNonNull(policy);
        this.tools = tools == null ? () -> null : tools;
        this.config = config == null ? new McpProperties.Token() : config;
        this.events = events == null ? e -> { } : events;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.cache = new McpTokenCache(this.store, this.config.getCacheSeconds() * 1000L, this.clock);
    }

    // ---------------------------------------------------------------- 查询

    public McpTokenView get(Actor actor, String id) { return McpTokenView.of(owned(actor, id), now()); }

    public McpTokenView.Page list(Actor actor, String ownerId, McpTokenStatus status, String keyword, int page, int size) {
        boolean manager = policy.canManage(actor.user());
        String self = accountId(actor.user());
        if (!manager) {
            if (ownerId != null && !ownerId.equals(self)) throw new McpException(403, "无权查看其他用户的令牌");
            ownerId = self;
        }
        var query = new McpTokenQuery(ownerId, status, keyword, page, size, now());
        long now = query.now();
        var items = store.list(query).stream().map(t -> McpTokenView.of(t, now)).toList();
        return new McpTokenView.Page(items, store.count(query), page, size);
    }

    // ---------------------------------------------------------------- 签发

    public Issued issue(Actor actor, IssueRequest request) {
        Objects.requireNonNull(request, "request");
        boolean manager = policy.canManage(actor.user());
        String self = accountId(actor.user());
        String ownerId = request.ownerId() == null || request.ownerId().isBlank() ? self : request.ownerId().strip();
        if (!ownerId.equals(self) && !manager) throw new McpException(403, "无权为其他用户签发令牌");
        var owner = identities.load(ownerId);
        if (owner == null) throw new IllegalArgumentException("所属账号不存在");
        if (!policy.canUse(owner)) throw new McpException(403, "所属账号无 MCP 使用权限");
        if (request.attributes() != null && !request.attributes().isEmpty() && !manager)
            throw new McpException(403, "只有管理员可以设置 attributes");
        int max = config.getMaxPerOwner();
        if (max > 0 && store.countUnrevoked(ownerId) >= max) throw new McpException(429, "该账号的令牌数量已达上限 " + max);

        long now = now();
        var scope = scope(owner, request.scope());
        String keyId = McpTokenCodec.newKeyId(), secret = McpTokenCodec.newSecret();
        var token = McpToken.builder()
                .id(McpStore.id()).keyId(keyId)
                .name(name(request.name())).remark(remark(request.remark()))
                .ownerId(ownerId).issuerId(self)
                .status(McpTokenStatus.ACTIVE)
                .expiresAt(expiresAt(request.expiresAt(), manager, now))
                .scope(scope)
                .attributes(attributes(request.attributes()))
                .allowedIps(McpIpRules.normalize(request.allowedIps()))
                .rateLimit(rateLimit(request.rateLimit()))
                .tokenHash(McpTokenCodec.hash(secret))
                .createdAt(now).updatedAt(now).version(1)
                .build();
        store.insert(token);
        cache.invalidate(keyId);
        var view = McpTokenView.of(token, now);
        var changes = new LinkedHashMap<String, Object>();
        changes.put("ownerId", ownerId);
        changes.put("expiresAt", token.expiresAt());
        changes.put("scope", token.scope());
        if (!token.attributes().isEmpty()) changes.put("attributes", token.attributes());
        if (!token.allowedIps().isEmpty()) changes.put("allowedIps", token.allowedIps());
        if (token.rateLimit() > 0) changes.put("rateLimit", token.rateLimit());
        publish(Action.ISSUE, view, actor, changes);
        return new Issued(view, McpTokenCodec.format(keyId, secret));
    }

    // ---------------------------------------------------------------- 修改

    public McpTokenView update(Actor actor, String id, UpdateRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.name() == null && request.remark() == null && request.expiresAt() == null && request.scope() == null
                && request.attributes() == null && request.allowedIps() == null && request.rateLimit() == null)
            throw new IllegalArgumentException("没有需要修改的字段");
        boolean manager = policy.canManage(actor.user());
        if (request.attributes() != null && !manager) throw new McpException(403, "只有管理员可以修改 attributes");
        var changes = new LinkedHashMap<String, Object>();
        var result = mutate(actor, id, current -> {
            changes.clear(); // 乐观锁重试时重新计算
            requireNotRevoked(current);
            long now = now();
            var b = current.toBuilder();
            if (request.name() != null) b.name(diff(changes, "name", current.name(), name(request.name())));
            if (request.remark() != null) b.remark(diff(changes, "remark", current.remark(), remark(request.remark())));
            if (request.expiresAt() != null)
                b.expiresAt(diff(changes, "expiresAt", current.expiresAt(), expiresAt(request.expiresAt(), manager, now)));
            if (request.scope() != null) {
                var owner = identities.load(current.ownerId());
                if (owner == null) throw new IllegalArgumentException("所属账号不存在");
                b.scope(diff(changes, "scope", current.scope(), scope(owner, request.scope())));
            }
            if (request.attributes() != null)
                b.attributes(diff(changes, "attributes", current.attributes(), attributes(request.attributes())));
            if (request.allowedIps() != null)
                b.allowedIps(diff(changes, "allowedIps", current.allowedIps(), McpIpRules.normalize(request.allowedIps())));
            if (request.rateLimit() != null)
                b.rateLimit(diff(changes, "rateLimit", current.rateLimit(), rateLimit(request.rateLimit())));
            return changes.isEmpty() ? null : b;
        });
        if (result.changed()) publish(Action.UPDATE, result.view(), actor, changes);
        return result.view();
    }

    /**
     * 重新生成密钥，keyId 不变。graceSeconds 内旧令牌仍可使用，便于客户端平滑切换；0 表示旧令牌立即失效。
     */
    public Issued rotate(Actor actor, String id, long graceSeconds) {
        if (graceSeconds < 0 || graceSeconds > config.getMaxRotateGraceSeconds())
            throw new IllegalArgumentException("graceSeconds 范围 0–" + config.getMaxRotateGraceSeconds());
        String secret = McpTokenCodec.newSecret();
        var result = mutate(actor, id, current -> {
            requireNotRevoked(current);
            long now = now();
            return current.toBuilder()
                    .tokenHash(McpTokenCodec.hash(secret))
                    .previousHash(graceSeconds > 0 ? current.tokenHash() : "")
                    .previousExpiresAt(graceSeconds > 0 ? now + graceSeconds * 1000 : 0)
                    .rotatedAt(now);
        });
        publish(Action.ROTATE, result.view(), actor, Map.of("graceSeconds", graceSeconds));
        return new Issued(result.view(), McpTokenCodec.format(result.view().keyId(), secret));
    }

    public McpTokenView disable(Actor actor, String id) {
        return status(actor, id, McpTokenStatus.DISABLED, Action.DISABLE);
    }

    public McpTokenView enable(Actor actor, String id) {
        return status(actor, id, McpTokenStatus.ACTIVE, Action.ENABLE);
    }

    /** 撤销不可恢复，轮换宽限期内的旧令牌也一并失效。 */
    public McpTokenView revoke(Actor actor, String id) {
        var result = mutate(actor, id, current -> {
            if (current.status() == McpTokenStatus.REVOKED) return null;
            return current.toBuilder().status(McpTokenStatus.REVOKED).previousHash("").previousExpiresAt(0);
        });
        if (result.changed()) publish(Action.REVOKE, result.view(), actor, Map.of());
        return result.view();
    }

    private McpTokenView status(Actor actor, String id, McpTokenStatus target, Action action) {
        var result = mutate(actor, id, current -> {
            requireNotRevoked(current);
            if (current.status() == target) return null;
            return current.toBuilder().status(target);
        });
        if (result.changed()) publish(action, result.view(), actor, Map.of());
        return result.view();
    }

    /** 物理删除，只允许已撤销或已过期的令牌。 */
    public void delete(Actor actor, String id) {
        var current = owned(actor, id);
        var status = current.effectiveStatus(now());
        if (status != McpTokenStatus.REVOKED && status != McpTokenStatus.EXPIRED)
            throw new McpException(409, "只能删除已撤销或已过期的令牌，请先撤销");
        store.delete(current.id());
        cache.invalidate(current.keyId());
        publish(Action.DELETE, McpTokenView.of(current, now()), actor, Map.of());
    }

    // ---------------------------------------------------------------- 认证使用

    /** 认证热路径：从全量内存缓存按 keyId 读取。 */
    public McpToken findByKeyId(String keyId) { return cache.get(keyId); }

    /** 后台任务重新确认：直接读库。 */
    public McpToken findById(String id) { return store.findById(id); }

    /** 记录最近使用时间和 IP，每个令牌每分钟最多写一次，失败只记日志。 */
    public void touch(McpToken token, String ip) {
        long now = now();
        if (token.lastUsedAt() > 0 && now - token.lastUsedAt() < MINUTE) return;
        synchronized (lastUseWrites) {
            Long previous = lastUseWrites.get(token.id());
            if (previous != null && now >= previous && now - previous < MINUTE) return;
            lastUseWrites.put(token.id(), now);
        }
        try { store.markUsed(token.id(), now, ip); }
        catch (RuntimeException e) { log.warn("MCP 令牌最近使用时间保存失败 keyId={}", token.keyId(), e); }
    }

    // ---------------------------------------------------------------- 内部

    private McpToken owned(Actor actor, String id) {
        var token = id == null ? null : store.findById(id);
        if (token == null) throw new McpException(404, "令牌不存在");
        if (!policy.canManage(actor.user()) && !token.ownerId().equals(accountId(actor.user())))
            throw new McpException(404, "令牌不存在");
        return token;
    }

    private record Mutation(McpTokenView view, boolean changed) { }

    /** 乐观锁读改写；change 返回 null 表示无需修改。 */
    private Mutation mutate(Actor actor, String id, Function<McpToken, McpToken.Builder> change) {
        for (int attempt = 0; attempt < 5; attempt++) {
            var current = owned(actor, id);
            var builder = change.apply(current);
            if (builder == null) return new Mutation(McpTokenView.of(current, now()), false);
            long now = now();
            var next = builder.updatedAt(Math.max(now, current.updatedAt() + 1)).version(current.version() + 1).build();
            if (store.update(next, current.version())) {
                cache.invalidate(next.keyId());
                return new Mutation(McpTokenView.of(next, now), true);
            }
        }
        throw new McpException(409, "令牌正在被其他请求修改，请重试");
    }

    private void publish(Action action, McpTokenView view, Actor actor, Map<String, Object> changes) {
        try { events.accept(new McpTokenEvent(action, view, accountId(actor.user()), actor.ip(), changes, now())); }
        catch (RuntimeException e) { log.warn("MCP 令牌事件处理失败 action={} keyId={}", action, view.keyId(), e); }
    }

    private static <T> T diff(Map<String, Object> changes, String field, T from, T to) {
        if (!Objects.equals(from, to)) {
            var change = new LinkedHashMap<String, Object>();
            change.put("from", from);
            change.put("to", to);
            changes.put(field, change);
        }
        return to;
    }

    private static void requireNotRevoked(McpToken token) {
        if (token.status() == McpTokenStatus.REVOKED) throw new McpException(409, "令牌已撤销，不能再修改");
    }

    private List<String> scope(IUserDetails owner, List<String> requested) {
        var scope = McpScope.normalize(requested);
        var registry = tools.get();
        for (String tool : McpScope.exactTools(scope)) {
            if (registry != null && !registry.contains(tool)) throw new IllegalArgumentException("未知工具: " + tool);
            if (!policy.canInvoke(owner, tool)) throw new McpException(403, "所属账号没有工具权限: " + tool);
        }
        return scope;
    }

    private long expiresAt(Long value, boolean manager, long now) {
        long deadline = value == null ? 0 : value;
        if (deadline < 0 || (deadline > 0 && deadline <= now))
            throw new IllegalArgumentException("expiresAt 必须为 0、null 或未来的毫秒时间戳");
        int maxDays = config.getMaxTtlDays();
        if (!manager && maxDays > 0) {
            long limit = now + maxDays * 86_400_000L;
            if (deadline == 0 || deadline > limit)
                throw new IllegalArgumentException("有效期最长 " + maxDays + " 天，必须设置过期时间");
        }
        return deadline;
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 60 || value.chars().anyMatch(c -> c < 32))
            throw new IllegalArgumentException("名称限 1–60 字符");
        return value.strip();
    }

    private static String remark(String value) {
        if (value == null) return "";
        if (value.length() > 255 || value.chars().anyMatch(c -> c < 32 && c != '\n')) throw new IllegalArgumentException("备注最多 255 字符");
        return value.strip();
    }

    private static Map<String, String> attributes(Map<String, String> value) {
        if (value == null) return Map.of();
        if (value.size() > 32) throw new IllegalArgumentException("attributes 最多 32 项");
        var result = new LinkedHashMap<String, String>();
        value.forEach((k, v) -> {
            if (k == null || !k.matches("[A-Za-z0-9_.:-]{1,64}")) throw new IllegalArgumentException("attributes 键限 1–64 位字母、数字或 _.:-");
            if (v == null || v.length() > 1024) throw new IllegalArgumentException("attributes 值不能为空且最多 1024 字符");
            result.put(k, v);
        });
        return result;
    }

    private static int rateLimit(Integer value) {
        int limit = value == null ? 0 : value;
        if (limit < 0 || limit > 100_000) throw new IllegalArgumentException("rateLimit 范围 0–100000（次/分钟），0 表示不限");
        return limit;
    }

    private String accountId(IUserDetails user) {
        String id = identities.accountId(user);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("账号标识不能为空");
        return id;
    }

    private long now() { return clock.millis(); }
}
