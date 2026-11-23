package com.yulinlin.mcp;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;
import java.util.*;
@Data @ConfigurationProperties("yulinlin.mcp")
public class McpProperties {
 private boolean enabled;
 private String applicationId;
 private String instanceId="local";
 private String group;
 private String path="/mcp";
 private String managementPath="/api/mcp";
 private boolean allowInsecure=true;
 private Path dataDirectory=Path.of("mcp-data");
 private Set<String> allowedOrigins=new HashSet<>();
 private int maxRequestBytes=4*1024*1024;
 private int retentionDays=30;
 private Command command=new Command();
 private Feature process=new Feature();
 private Feature monitor=new Feature();
 @Data public static class Feature { private boolean enabled; }
 @Data public static class Command {
  private boolean enabled;
  private String shell="/bin/bash";
  private int maxConcurrent=4;
  private int maxCommandBytes=65536;
  private int defaultTimeoutSeconds=3600;
  private long maxOutputBytes=8388608;
 }
 public void validate(){
  if(applicationId==null||!applicationId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("yulinlin.mcp.application-id must be stable and contain 1-64 letters/digits/_/-");
  if(instanceId==null||!instanceId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid MCP instance-id");
  if(!path.matches("/[A-Za-z0-9/_-]+")||!managementPath.matches("/[A-Za-z0-9/_-]+")||path.equals(managementPath)) throw new IllegalArgumentException("Invalid or conflicting MCP paths");
  if(maxRequestBytes<1||maxRequestBytes>64*1024*1024||retentionDays<1||command.maxConcurrent<1||command.maxCommandBytes<1||command.maxOutputBytes<1||command.defaultTimeoutSeconds<0||command.defaultTimeoutSeconds>604800)throw new IllegalArgumentException("Invalid MCP limits");
 }
}
