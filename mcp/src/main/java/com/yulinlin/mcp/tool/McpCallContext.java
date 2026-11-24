package com.yulinlin.mcp.tool;
import com.yulinlin.mcp.auth.McpPrincipal;
import com.yulinlin.mcp.auth.McpScope;
import com.yulinlin.security.auth.IUserDetails;
import java.util.*;

/**
 * 一次工具调用的上下文。{@code @McpTool} 方法可以直接声明 McpCallContext 参数，
 * 或在调用链中通过 {@link #current()} 获取，用 {@link #attribute} 读取令牌上的业务属性做数据范围限制。
 */
public record McpCallContext(String applicationId, McpPrincipal principal, IUserDetails user, String requestId, String contextPath) {
 private static final ThreadLocal<McpCallContext> CURRENT=new ThreadLocal<>();
 public McpCallContext { Objects.requireNonNull(principal,"principal"); }
 public String credentialId(){return principal.credentialId();}
 public List<String> scope(){return principal.scope();}
 public Map<String,String> attributes(){return principal.attributes();}
 public String attribute(String key){return principal.attribute(key);}
 public boolean allowsTool(String name,boolean readOnly){return McpScope.allowsTool(principal.scope(),name,readOnly);}
 /** 当前线程正在执行的工具调用；不在工具调用中时为 null。 */
 public static McpCallContext current(){return CURRENT.get();}
 static <T> T with(McpCallContext context,java.util.concurrent.Callable<T> action)throws Exception{
  var previous=CURRENT.get();CURRENT.set(context);
  try{return action.call();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
 }
}
