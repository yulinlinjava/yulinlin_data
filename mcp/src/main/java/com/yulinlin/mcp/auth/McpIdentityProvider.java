package com.yulinlin.mcp.auth;
import com.yulinlin.security.auth.IUserDetails;
/** Load current authorization by stable account key. Return null for missing/disabled users. */
@FunctionalInterface public interface McpIdentityProvider {
 IUserDetails load(String accountId);
 default String accountId(IUserDetails user) { return user.getUserId(); }
}
