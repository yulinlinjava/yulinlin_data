package com.yulinlin.mcp.auth;

import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.security.auth.IUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/** 认证入口：全局开关、依次调用认证器、加载账号并校验 MCP 使用权限和工具权限。 */
public class McpAuthentication {
    private final List<McpAuthenticator> authenticators;
    private final McpStore store;
    private final McpIdentityProvider identities;
    private final McpAccessPolicy policy;

    public McpAuthentication(List<McpAuthenticator> authenticators, McpStore store,
                             McpIdentityProvider identities, McpAccessPolicy policy) {
        this.authenticators = List.copyOf(authenticators);
        this.store = Objects.requireNonNull(store);
        this.identities = Objects.requireNonNull(identities);
        this.policy = Objects.requireNonNull(policy);
        var types = new HashSet<String>();
        for (var a : this.authenticators)
            if (!types.add(a.type())) throw new IllegalArgumentException("重复的 MCP 认证器类型: " + a.type());
    }

    public boolean enabled() { var s = store.get("settings", Settings.class); return s != null && s.enabled(); }

    public void enable(boolean enabled) { store.save("settings", "settings", new Settings(enabled)); }

    /** 认证请求；成功时账号已加载并通过 {@link McpAccessPolicy#canUse}。 */
    public McpPrincipal authenticate(HttpServletRequest request) {
        if (!enabled()) throw new McpException(403, "MCP 已暂停");
        for (var authenticator : authenticators) {
            var principal = authenticator.authenticate(request);
            if (principal == null) continue;
            if (!authenticator.type().equals(principal.type()))
                throw new IllegalStateException("认证器返回的 type 与 type() 不一致: " + authenticator.type());
            requireUser(principal);
            return principal;
        }
        throw unauthorized();
    }

    /** 按身份加载当前账号，不重新校验凭证本身。用于同一请求内。 */
    public IUserDetails requireUser(McpPrincipal principal) {
        if (!enabled()) throw unauthorized();
        var user = identities.load(principal.accountId());
        if (user == null || !policy.canUse(user)) throw unauthorized();
        return user;
    }

    /** 重新确认凭证仍然有效并返回最新身份。用于后台任务、延迟执行。 */
    public McpPrincipal refresh(McpPrincipal principal) {
        if (!enabled()) throw unauthorized();
        var authenticator = authenticators.stream().filter(a -> a.type().equals(principal.type())).findFirst()
                .orElseThrow(McpAuthentication::unauthorized);
        var current = authenticator.refresh(principal);
        if (current == null) throw unauthorized();
        return current;
    }

    /** 重新确认凭证、账号和工具权限，返回账号。 */
    public IUserDetails refreshTool(McpPrincipal principal, String tool, boolean readOnly) {
        var current = refresh(principal);
        var user = requireUser(current);
        requireTool(current, user, tool, readOnly);
        return user;
    }

    public void requireTool(McpPrincipal principal, IUserDetails user, String tool, boolean readOnly) {
        if (!McpScope.allowsTool(principal.scope(), tool, readOnly) || !policy.canInvoke(user, tool))
            throw new McpException(403, "无此工具调用权限");
    }

    public static McpException unauthorized() { return new McpException(401, "MCP 凭证或当前账号授权无效"); }

    public record Settings(boolean enabled) { }
}
