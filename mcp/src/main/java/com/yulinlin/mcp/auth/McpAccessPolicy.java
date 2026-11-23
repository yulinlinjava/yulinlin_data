package com.yulinlin.mcp.auth;
import com.yulinlin.security.auth.IUserDetails;
/** All decisions must be supplied by the application; no implicit grants. */
public interface McpAccessPolicy {
 boolean canUse(IUserDetails user);
 boolean canManage(IUserDetails user);
 boolean canInvoke(IUserDetails user, String toolName);
}
