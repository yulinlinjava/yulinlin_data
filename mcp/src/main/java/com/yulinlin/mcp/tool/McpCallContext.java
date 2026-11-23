package com.yulinlin.mcp.tool;
import com.yulinlin.security.auth.IUserDetails;
public record McpCallContext(String applicationId,String credentialId,IUserDetails user,String requestId,String contextPath){}
