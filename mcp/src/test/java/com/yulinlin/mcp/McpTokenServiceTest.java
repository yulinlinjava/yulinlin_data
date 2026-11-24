package com.yulinlin.mcp;

import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.token.McpTokenEvent.Action;
import com.yulinlin.mcp.token.McpTokenService.*;
import com.yulinlin.mcp.tool.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpTokenServiceTest extends McpTestSupport {
    static IssueRequest issue(String name) { return IssueRequest.of(name); }
    static UpdateRequest rename(String name) { return new UpdateRequest(name, null, null, null, null, null, null); }

    @Test void issuedTokenIsShownOnceAndOnlyItsHashIsStored() {
        var env = new TokenEnv();
        var issued = env.service.issue(env.actor(ADMIN), issue(" client "));
        var parsed = McpTokenCodec.parse(issued.token());
        assertNotNull(parsed);
        assertEquals(parsed.keyId(), issued.credential().keyId());
        assertEquals("client", issued.credential().name());
        assertEquals("ylmcp_" + parsed.keyId() + "_****", issued.credential().display());
        var stored = env.tokens.findById(issued.credential().id());
        assertEquals(McpTokenCodec.hash(parsed.secret()), stored.tokenHash());
        assertFalse(stored.toString().contains(parsed.secret()));
        assertFalse(issued.toString().contains(parsed.secret()));
        assertEquals("admin", stored.issuerId());
        assertEquals(McpTokenStatus.ACTIVE, issued.credential().status());
        assertEquals(Action.ISSUE, env.events.getFirst().action());
    }

    @Test void ownersOnlySeeAndChangeTheirOwnTokensManagersSeeAll() {
        var env = new TokenEnv(); env.policy.manage = false;
        var mine = env.service.issue(env.actor(READER), issue("mine")).credential();
        env.policy.manage = true;
        var admin = env.service.issue(env.actor(ADMIN), issue("admin")).credential();
        env.policy.manage = false;
        assertEquals(List.of(mine.id()), env.service.list(env.actor(READER), null, null, null, 1, 20).items().stream().map(McpTokenView::id).toList());
        assertThrows(McpException.class, () -> env.service.list(env.actor(READER), "admin", null, null, 1, 20));
        assertThrows(McpException.class, () -> env.service.get(env.actor(READER), admin.id()));
        assertThrows(McpException.class, () -> env.service.revoke(env.actor(READER), admin.id()));
        assertThrows(McpException.class, () -> env.service.update(env.actor(READER), admin.id(), rename("x")));
        assertThrows(McpException.class, () -> env.service.issue(env.actor(READER),
                new IssueRequest("for admin", null, "admin", null, null, null, null, null)));
        assertThrows(McpException.class, () -> env.service.issue(env.actor(READER),
                new IssueRequest("attrs", null, null, null, null, Map.of("tenant", "t1"), null, null)));
        env.policy.manage = true;
        assertEquals(2, env.service.list(env.actor(ADMIN), null, null, null, 1, 20).total());
        var delegated = env.service.issue(env.actor(ADMIN), new IssueRequest("delegated", null, "reader", null, null, Map.of("tenant", "t1"), null, null));
        assertEquals("reader", delegated.credential().ownerId());
        assertEquals("admin", delegated.credential().issuerId());
        assertEquals(Map.of("tenant", "t1"), delegated.credential().attributes());
    }

    @Test void updateValidatesAndRecordsOnlyChangedFields() throws Exception {
        var tool = new McpToolDefinition("echo", "echo", JSON.readTree("{\"type\":\"object\"}"), true, (c, a) -> a);
        var policy = new Policy();
        var registry = new McpToolRegistry(List.of(() -> List.of(tool)), policy);
        var env = new TokenEnv(policy, id -> new User(id), registry);
        var token = env.issue(ADMIN, "client", null).credential();
        assertThrows(IllegalArgumentException.class, () -> env.service.update(env.actor(ADMIN), token.id(), new UpdateRequest(null, null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> env.service.update(env.actor(ADMIN), token.id(), new UpdateRequest(null, null, null, List.of("missing"), null, null, null)));
        policy.invoke = false;
        assertThrows(McpException.class, () -> env.service.update(env.actor(ADMIN), token.id(), new UpdateRequest(null, null, null, List.of("echo"), null, null, null)));
        policy.invoke = true;
        env.events.clear();
        var updated = env.service.update(env.actor(ADMIN), token.id(),
                new UpdateRequest("client", "备注", 5000L, List.of("echo"), Map.of("k", "v"), List.of("10.0.0.0/8"), 30));
        assertEquals(List.of("echo"), updated.scope());
        assertEquals(List.of("10.0.0.0/8"), updated.allowedIps());
        assertEquals(5000L, updated.expiresAt());
        var changes = env.events.getFirst().changes();
        assertFalse(changes.containsKey("name"));
        assertTrue(changes.keySet().containsAll(List.of("remark", "expiresAt", "scope", "attributes", "allowedIps", "rateLimit")));
        env.events.clear();
        env.service.update(env.actor(ADMIN), token.id(), rename("client"));
        assertTrue(env.events.isEmpty(), "no-op update publishes nothing");
        assertThrows(IllegalArgumentException.class, () -> env.service.update(env.actor(ADMIN), token.id(), new UpdateRequest(null, null, null, null, null, List.of("not-an-ip"), null)));
        assertThrows(IllegalArgumentException.class, () -> env.service.update(env.actor(ADMIN), token.id(), new UpdateRequest(null, null, 500L, null, null, null, null)));
        assertEquals(2, env.tokens.findById(token.id()).version());
    }

    @Test void lifecycleDisableEnableRevokeDelete() {
        var env = new TokenEnv();
        var id = env.issue(ADMIN, "client", null).credential().id();
        assertThrows(McpException.class, () -> env.service.delete(env.actor(ADMIN), id));
        assertEquals(McpTokenStatus.DISABLED, env.service.disable(env.actor(ADMIN), id).status());
        assertEquals(McpTokenStatus.ACTIVE, env.service.enable(env.actor(ADMIN), id).status());
        assertEquals(McpTokenStatus.REVOKED, env.service.revoke(env.actor(ADMIN), id).status());
        assertThrows(McpException.class, () -> env.service.enable(env.actor(ADMIN), id));
        assertThrows(McpException.class, () -> env.service.update(env.actor(ADMIN), id, rename("x")));
        assertThrows(McpException.class, () -> env.service.rotate(env.actor(ADMIN), id, 0));
        int before = env.events.size();
        env.service.revoke(env.actor(ADMIN), id);
        assertEquals(before, env.events.size(), "repeated revoke is idempotent");
        env.service.delete(env.actor(ADMIN), id);
        assertNull(env.tokens.findById(id));
        assertEquals(List.of(Action.ISSUE, Action.DISABLE, Action.ENABLE, Action.REVOKE, Action.DELETE),
                env.events.stream().map(McpTokenEvent::action).toList());
    }

    @Test void expiryIsComputedRenewableAndBoundedForNonManagers() {
        var env = new TokenEnv();
        var id = env.service.issue(env.actor(ADMIN), new IssueRequest("temp", null, null, 2000L, null, null, null, null)).credential().id();
        env.time.now = 2000;
        assertEquals(McpTokenStatus.EXPIRED, env.service.get(env.actor(ADMIN), id).status());
        assertEquals(1, env.service.list(env.actor(ADMIN), null, McpTokenStatus.EXPIRED, null, 1, 20).total());
        assertEquals(0, env.service.list(env.actor(ADMIN), null, McpTokenStatus.ACTIVE, null, 1, 20).total());
        assertEquals(McpTokenStatus.ACTIVE, env.service.update(env.actor(ADMIN), id, new UpdateRequest(null, null, 0L, null, null, null, null)).status());
        env.properties.getToken().setMaxTtlDays(1);
        var limited = new McpTokenService(env.tokens, id2 -> new User(id2), env.policy, () -> null, env.properties.getToken(), e -> { }, env.time);
        env.policy.manage = false;
        assertThrows(IllegalArgumentException.class, () -> limited.issue(env.actor(READER), issue("forever")));
        assertThrows(IllegalArgumentException.class, () -> limited.issue(env.actor(READER), new IssueRequest("long", null, null, env.time.now + 2 * 86_400_000L, null, null, null, null)));
        assertNotNull(limited.issue(env.actor(READER), new IssueRequest("short", null, null, env.time.now + 3_600_000L, null, null, null, null)));
        env.policy.manage = true;
        assertNotNull(limited.issue(env.actor(ADMIN), issue("manager forever")));
    }

    @Test void perOwnerLimitIgnoresRevokedTokens() {
        var env = new TokenEnv();
        env.properties.getToken().setMaxPerOwner(2);
        var service = new McpTokenService(env.tokens, id -> new User(id), env.policy, () -> null, env.properties.getToken(), e -> { }, env.time);
        var first = service.issue(env.actor(ADMIN), issue("1")).credential().id();
        service.issue(env.actor(ADMIN), issue("2"));
        assertEquals(429, assertThrows(McpException.class, () -> service.issue(env.actor(ADMIN), issue("3"))).status());
        service.revoke(env.actor(ADMIN), first);
        assertNotNull(service.issue(env.actor(ADMIN), issue("3")));
    }

    @Test void ownerWithoutMcpPermissionCannotReceiveTokens() {
        var env = new TokenEnv(new Policy(), id -> id.equals("ghost") ? null : new User(id), null);
        assertThrows(IllegalArgumentException.class, () -> env.service.issue(env.actor(ADMIN), new IssueRequest("x", null, "ghost", null, null, null, null, null)));
        env.policy.use = false;
        assertThrows(McpException.class, () -> env.issue(ADMIN, "x", null));
    }

    @Test void concurrentModificationRetriesOnVersionConflict() {
        var env = new TokenEnv();
        var id = env.issue(ADMIN, "client", null).credential().id();
        var conflicts = new int[]{2};
        var flaky = new MemoryTokenStore() {
            { rows.putAll(env.tokens.rows); }
            @Override public synchronized boolean update(McpToken t, long expected) {
                if (conflicts[0]-- > 0) return false;
                return super.update(t, expected);
            }
        };
        var service = new McpTokenService(flaky, i -> new User(i), env.policy, () -> null, env.properties.getToken(), e -> { }, env.time);
        assertEquals("renamed", service.update(env.actor(ADMIN), id, rename("renamed")).name());
        conflicts[0] = 100;
        assertEquals(409, assertThrows(McpException.class, () -> service.update(env.actor(ADMIN), id, rename("again"))).status());
    }

    @Test void touchWritesUsageAtMostOncePerMinute() {
        var env = new TokenEnv();
        var id = env.issue(ADMIN, "client", null).credential().id();
        env.time.now = 100_000;
        env.service.touch(env.tokens.findById(id), "10.0.0.1");
        assertEquals(100_000, env.tokens.findById(id).lastUsedAt());
        env.time.now = 130_000;
        env.service.touch(env.tokens.findById(id), "10.0.0.2");
        assertEquals("10.0.0.1", env.tokens.findById(id).lastUsedIp());
        env.time.now = 161_000;
        env.service.touch(env.tokens.findById(id), "10.0.0.3");
        assertEquals("10.0.0.3", env.tokens.findById(id).lastUsedIp());
        assertEquals(161_000, env.service.get(env.actor(ADMIN), id).lastUsedAt());
    }

}
