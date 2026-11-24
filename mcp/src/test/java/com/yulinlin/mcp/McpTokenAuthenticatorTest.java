package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.token.McpTokenService.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class McpTokenAuthenticatorTest extends McpTestSupport {
    static int status(Runnable action) { return assertThrows(McpException.class, action::run).status(); }

    @Test void tokenFormatIsParsedByFixedLengthAndForeignTokensAreSkipped() {
        var secret = McpTokenCodec.newSecret();
        assertEquals(43, secret.length());
        var parsed = McpTokenCodec.parse(McpTokenCodec.format("abc123def456", secret));
        assertEquals("abc123def456", parsed.keyId());
        assertEquals(secret, parsed.secret());
        for (var bad : List.of("ylmcp_short", "ylmcp_ABC123DEF456_" + secret, "other_abc123def456_" + secret, "ylmcp_abc123def456-" + secret))
            assertNull(McpTokenCodec.parse(bad), bad);
        var env = new TokenEnv();
        assertNull(env.authenticator.authenticate(new MockHttpServletRequest()));
        assertNull(env.authenticator.authenticate(request("some-other-jwt")));
        assertEquals(401, status(() -> env.login("some-other-jwt")));
        var twice = request("x");
        twice.addHeader("Authorization", "Bearer y");
        assertEquals(401, status(() -> env.authenticator.authenticate(twice)));
    }

    @Test void validTokenYieldsPrincipalWithScopeAndAttributes() {
        var env = new TokenEnv();
        var issued = env.service.issue(env.actor(ADMIN), new IssueRequest("client", null, "reader", null, List.of("echo"), Map.of("netdisk.root", "/a"), null, null));
        var principal = env.login(issued.token());
        assertEquals(TokenAuthenticator.TYPE, principal.type());
        assertEquals(issued.credential().id(), principal.credentialId());
        assertEquals("reader", principal.accountId());
        assertEquals(List.of("echo"), principal.scope());
        assertEquals("/a", principal.attribute("netdisk.root"));
        var wrongSecret = McpTokenCodec.format(issued.credential().keyId(), McpTokenCodec.newSecret());
        assertEquals(401, status(() -> env.login(wrongSecret)));
        var unknownKey = McpTokenCodec.format("zzzzzzzzzzzz", McpTokenCodec.newSecret());
        assertEquals(401, status(() -> env.login(unknownKey)));
    }

    @Test void statusExpiryAccountPolicyAndGlobalSwitchAreEnforced() {
        var active = new AtomicBoolean(true);
        var env = new TokenEnv(new Policy(), id -> active.get() ? new User(id) : null, null);
        var issued = env.service.issue(env.actor(ADMIN), new IssueRequest("temp", null, null, 5000L, null, null, null, null));
        String token = issued.token(), id = issued.credential().id();
        assertNotNull(env.login(token));
        env.service.disable(env.actor(ADMIN), id);
        assertEquals(401, status(() -> env.login(token)));
        env.service.enable(env.actor(ADMIN), id);
        active.set(false);
        assertEquals(401, status(() -> env.login(token)));
        active.set(true);
        env.policy.use = false;
        assertEquals(401, status(() -> env.login(token)));
        env.policy.use = true;
        env.auth.enable(false);
        assertEquals(403, status(() -> env.login(token)));
        env.auth.enable(true);
        env.time.now = 5000;
        assertEquals(401, status(() -> env.login(token)));
        env.time.now = 1000;
        env.service.revoke(env.actor(ADMIN), id);
        assertEquals(401, status(() -> env.login(token)));
    }

    @Test void rotationKeepsOldSecretOnlyDuringGracePeriod() {
        var env = new TokenEnv();
        var first = env.issue(ADMIN, "client", null);
        var id = first.credential().id();
        var second = env.service.rotate(env.actor(ADMIN), id, 60);
        assertEquals(first.credential().keyId(), second.credential().keyId());
        assertNotEquals(first.token(), second.token());
        assertEquals(61_000, second.credential().previousExpiresAt());
        assertNotNull(env.login(first.token()));
        assertNotNull(env.login(second.token()));
        env.time.now = 61_000;
        assertEquals(401, status(() -> env.login(first.token())));
        assertNotNull(env.login(second.token()));
        var third = env.service.rotate(env.actor(ADMIN), id, 0);
        assertEquals(401, status(() -> env.login(second.token())));
        assertNotNull(env.login(third.token()));
        var fourth = env.service.rotate(env.actor(ADMIN), id, 3600);
        env.service.revoke(env.actor(ADMIN), id);
        assertEquals(401, status(() -> env.login(third.token())));
        assertEquals(401, status(() -> env.login(fourth.token())));
        assertThrows(IllegalArgumentException.class, () -> env.service.rotate(env.actor(ADMIN), env.issue(ADMIN, "x", null).credential().id(), -1));
    }

    @Test void ipAllowlistAndRateLimitAreChecked() {
        var env = new TokenEnv();
        var token = env.service.issue(env.actor(ADMIN), new IssueRequest("ip", null, null, null, null, null, List.of("10.1.0.0/16", "::1"), 2)).token();
        var inside = request(token); inside.setRemoteAddr("10.1.2.3");
        assertNotNull(env.auth.authenticate(inside));
        var v6 = request(token); v6.setRemoteAddr("0:0:0:0:0:0:0:1");
        assertNotNull(env.auth.authenticate(v6));
        var outside = request(token); outside.setRemoteAddr("10.2.0.1");
        assertEquals(403, status(() -> env.auth.authenticate(outside)));
        assertEquals(429, status(() -> env.auth.authenticate(inside)));
        env.time.now += 60_000;
        assertNotNull(env.auth.authenticate(inside));
    }

    @Test void refreshSeesRevocationForBackgroundWork() {
        var env = new TokenEnv();
        var issued = env.issue(ADMIN, "client", List.of("command_execute"));
        var principal = env.login(issued.token());
        assertEquals(ADMIN.getUserId(), env.auth.refreshTool(principal, "command_execute", false).getUserId());
        assertEquals(403, status(() -> env.auth.refreshTool(principal, "other", true)));
        env.service.update(env.actor(ADMIN), issued.credential().id(), new UpdateRequest(null, null, null, List.of("@readonly"), null, null, null));
        assertEquals(403, status(() -> env.auth.refreshTool(principal, "command_execute", false)));
        env.service.revoke(env.actor(ADMIN), issued.credential().id());
        assertEquals(401, status(() -> env.auth.refresh(principal)));
    }

    @Test void allTokensAreCachedInMemoryAndReloadedOnLocalChangeOrTtl() {
        var env = new TokenEnv();
        env.properties.getToken().setCacheSeconds(30);
        var loads = new int[1]; var lookups = new int[1];
        var counting = new MemoryTokenStore() {
            @Override public List<McpToken> list(McpTokenQuery q) { loads[0]++; return super.list(q); }
            @Override public McpToken findByKeyId(String keyId) { lookups[0]++; return super.findByKeyId(keyId); }
        };
        var service = new McpTokenService(counting, id -> new User(id), env.policy, () -> null, env.properties.getToken(), e -> { }, env.time);
        var auth = new McpAuthentication(List.of(new TokenAuthenticator(service, env.time)), env.store, id -> new User(id), env.policy);
        var issued = service.issue(Actor.of(ADMIN), IssueRequest.of("cached"));
        for (int i = 0; i < 5; i++) assertNotNull(auth.authenticate(request(issued.token())));
        assertEquals(1, loads[0], "whole table loaded once");
        var unknown = McpTokenCodec.format("zzzzzzzzzzzz", McpTokenCodec.newSecret());
        for (int i = 0; i < 3; i++) assertEquals(401, status(() -> auth.authenticate(request(unknown))));
        assertEquals(1, loads[0], "unknown keyId never hits the store");
        service.disable(Actor.of(ADMIN), issued.credential().id());
        assertEquals(401, status(() -> auth.authenticate(request(issued.token()))));
        assertEquals(2, loads[0], "local change reloads immediately");
        // 其他实例直接改库：TTL 内仍是旧值，过期后重新加载
        var current = counting.findById(issued.credential().id());
        counting.update(current.toBuilder().status(McpTokenStatus.ACTIVE).version(current.version() + 1).build(), current.version());
        assertEquals(401, status(() -> auth.authenticate(request(issued.token()))));
        env.time.now += 30_000;
        assertNotNull(auth.authenticate(request(issued.token())));
        assertEquals(3, loads[0]);
        assertEquals(0, lookups[0], "hot path never queries by keyId");
    }

    @Test void duplicateAuthenticatorTypesAreRejected() {
        var env = new TokenEnv();
        assertThrows(IllegalArgumentException.class, () -> new McpAuthentication(List.of(env.authenticator, env.authenticator), env.store, id -> ADMIN, env.policy));
    }
}
