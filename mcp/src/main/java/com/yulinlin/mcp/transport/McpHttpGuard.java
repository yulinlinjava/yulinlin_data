package com.yulinlin.mcp.transport;
import com.yulinlin.mcp.*;
import jakarta.servlet.http.*;
import java.net.URI;
import java.util.Set;
public class McpHttpGuard {
 private final McpProperties properties;
 public McpHttpGuard(McpProperties properties){this.properties=properties;}
 public void check(HttpServletRequest request){
  boolean local=Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())&&Set.of("localhost","127.0.0.1","::1","[::1]").contains(request.getServerName());
  if(!request.isSecure()&&!properties.isAllowInsecure()&&!local)throw new McpException(403,"远程 MCP 需要 HTTPS");
  String origin=request.getHeader("Origin");if(origin==null)return;
  try{URI uri=URI.create(origin);int port=uri.getPort()<0?("https".equals(uri.getScheme())?443:80):uri.getPort();
   if(uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!uri.getRawPath().isEmpty()||!Set.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException();
   if(!properties.getAllowedOrigins().contains(origin)&&!(uri.getHost().equalsIgnoreCase(request.getServerName())&&port==request.getServerPort()&&uri.getScheme().equalsIgnoreCase(request.getScheme())))throw new IllegalArgumentException();
  }catch(RuntimeException e){throw new McpException(403,"MCP 请求来源不合法");}
 }
}
