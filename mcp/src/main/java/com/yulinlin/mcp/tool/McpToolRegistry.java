package com.yulinlin.mcp.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import com.networknt.schema.dialect.Dialects;
import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.auth.McpAccessPolicy;
import com.yulinlin.security.auth.IUserDetails;
import java.util.*;
public class McpToolRegistry {
 private final Map<String,McpToolDefinition> tools=new LinkedHashMap<>();
 private final Map<String,Schema> inputs=new HashMap<>(),outputs=new HashMap<>();
 private final SchemaRegistry schemas=SchemaRegistry.withDialect(Dialects.getDraft202012());
 private final McpAccessPolicy policy;
 public McpToolRegistry(Collection<McpToolProvider> providers,McpAccessPolicy policy){this.policy=policy;for(var provider:providers)for(var tool:provider.tools())register(tool);}
 // Startup registration only; annotation discovery runs after Spring has created its proxies.
 void register(McpToolDefinition tool){
  if(tools.containsKey(tool.name()))throw new IllegalArgumentException("Duplicate MCP tool: "+tool.name());
  inputs.put(tool.name(),compile(tool.inputSchema()));if(tool.outputSchema()!=null)outputs.put(tool.name(),compile(tool.outputSchema()));
  tools.put(tool.name(),tool);
 }
 private Schema compile(JsonNode node){var meta=schemas.getSchema(SchemaLocation.of(Dialects.getDraft202012().getId()));if(!meta.validate(node.toString(),InputFormat.JSON).isEmpty())throw new IllegalArgumentException("Invalid MCP JSON Schema");return schemas.getSchema(node.toString(),InputFormat.JSON);}
 private boolean allowed(McpCallContext context,McpToolDefinition tool){return context.allowsTool(tool.name(),tool.readOnly())&&policy.canUse(context.user())&&policy.canInvoke(context.user(),tool.name());}
 public List<Map<String,Object>> list(McpCallContext context){return tools.values().stream().filter(t->allowed(context,t)).map(McpToolDefinition::descriptor).toList();}
 /** 管理端工具目录：当前账号有权限的工具，供签发令牌时选择范围。 */
 public List<Map<String,Object>> catalog(IUserDetails user){
  return tools.values().stream().filter(t->policy.canInvoke(user,t.name())).map(t->{var m=new LinkedHashMap<String,Object>();m.put("name",t.name());m.put("description",t.description());m.put("readOnly",t.readOnly());return (Map<String,Object>)m;}).toList();
 }
 public boolean contains(String name){return tools.containsKey(name);}
 public boolean readOnly(String name){var tool=tools.get(name);return tool!=null&&tool.readOnly();}
 public void validate(McpCallContext context,String name,JsonNode args){
  var tool=tools.get(name);
  if(tool==null)throw new IllegalArgumentException("Unknown tool");
  if(!allowed(context,tool))throw new McpException(403,"无此工具调用权限");
  if(!args.isObject()||!inputs.get(name).validate(args.toString(),InputFormat.JSON).isEmpty())throw new IllegalArgumentException("工具参数不符合 inputSchema");
 }
 public Object call(McpCallContext context,String name,JsonNode args)throws Exception{validate(context,name,args);var tool=tools.get(name);return McpCallContext.with(context,()->tool.handler().execute(context,args));}
 /** outputSchema describes the full structuredContent, including the compatibility data envelope. */
 public void validateOutput(String name,JsonNode payload){if(outputs.containsKey(name)&&!outputs.get(name).validate(payload.toString(),InputFormat.JSON).isEmpty())throw new IllegalStateException("工具输出不符合 outputSchema，操作可能已经执行");}
}
