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
 /** initialize 返回给模型的服务说明。不配时按实际启用的工具组生成。 */
 private String instructions;
 private Token token=new Token();
 private DataAccess data=new DataAccess();
 private Command command=new Command();
 private Feature process=new Feature();
 private Feature monitor=new Feature();
 @Data public static class Feature { private boolean enabled; }
 /** 令牌策略 */
 @Data public static class Token {
  /** 非管理员签发/续期的最长有效天数，0 表示不限（也允许永久令牌） */
  private int maxTtlDays=0;
  /** 每个账号未撤销令牌的数量上限，0 表示不限 */
  private int maxPerOwner=50;
  /** 认证缓存秒数；多实例部署时，其他实例最多延迟这么久看到禁用/撤销。0 表示不缓存 */
  private int cacheSeconds=10;
  /** 轮换时旧令牌宽限期上限（秒） */
  private int maxRotateGraceSeconds=7*86400;
 }
 /** 数据工具（data.*）。配置了 read 或 write 才启用；见 doc/mcp/data.md */
 @Data public static class DataAccess {
  /** 可读的数据源组；"*" 表示项目中全部 JDBC / Elasticsearch 数据源。强烈建议使用只读账号 */
  private List<String> read=new ArrayList<>();
  /** 可写的数据源组（仅 JDBC），开放 data.execute；写组自动可读 */
  private List<String> write=new ArrayList<>();
  /** 单次查询返回给 AI 的最大行数，超出截断 */
  private int maxRows=1000;
  /** 单次工具调用（含沙箱脚本）的总时长上限：JDBC 为事务超时，Elasticsearch 写进命令的服务端超时 */
  private int timeoutSeconds=30;
  /** data.analyze 脚本内所有查询合计最多读取的行数，超出报错（不截断）。行数据会整批放进内存，调大前先评估堆内存 */
  private int scriptRowBudget=200_000;
 }
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
  if(instructions!=null&&instructions.length()>8000)throw new IllegalArgumentException("yulinlin.mcp.instructions 最长 8000 字符");
  if(maxRequestBytes<1||maxRequestBytes>64*1024*1024||retentionDays<1||command.maxConcurrent<1||command.maxCommandBytes<1||command.maxOutputBytes<1||command.defaultTimeoutSeconds<0||command.defaultTimeoutSeconds>604800)throw new IllegalArgumentException("Invalid MCP limits");
  for(var g:data.read)if(g==null||g.isBlank())throw new IllegalArgumentException("yulinlin.mcp.data.read 不能有空的组名");
  for(var g:data.write)if(g==null||g.isBlank()||g.equals("*"))throw new IllegalArgumentException("yulinlin.mcp.data.write 必须写明数据源组名，不支持 *");
  if(data.maxRows<1||data.maxRows>100000||data.timeoutSeconds<1||data.timeoutSeconds>3600||data.scriptRowBudget<1)throw new IllegalArgumentException("Invalid MCP data limits: max-rows 1–100000, timeout-seconds 1–3600, script-row-budget >= 1");
  if(token.maxTtlDays<0||token.maxPerOwner<0||token.cacheSeconds<0||token.cacheSeconds>300||token.maxRotateGraceSeconds<0||token.maxRotateGraceSeconds>30*86400)throw new IllegalArgumentException("Invalid MCP token limits");
 }
}
