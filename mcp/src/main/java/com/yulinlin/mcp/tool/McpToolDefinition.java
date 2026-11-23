package com.yulinlin.mcp.tool;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
public record McpToolDefinition(String name,String description,JsonNode inputSchema,JsonNode outputSchema,boolean readOnly,McpToolHandler handler){
 public McpToolDefinition { Objects.requireNonNull(handler); if(name==null||!name.matches("[A-Za-z0-9_.-]{1,128}")||description==null||inputSchema==null||!inputSchema.isObject()||(outputSchema!=null&&!outputSchema.isObject()))throw new IllegalArgumentException("Invalid MCP tool definition"); inputSchema=inputSchema.deepCopy();if(outputSchema!=null)outputSchema=outputSchema.deepCopy(); }
 public McpToolDefinition(String name,String description,JsonNode schema,boolean readOnly,McpToolHandler handler){this(name,description,schema,null,readOnly,handler);}
 @Override public JsonNode inputSchema(){return inputSchema.deepCopy();}
 @Override public JsonNode outputSchema(){return outputSchema==null?null:outputSchema.deepCopy();}
 public Map<String,Object> descriptor(){var m=new LinkedHashMap<String,Object>();m.put("name",name);m.put("description",description);m.put("inputSchema",inputSchema());m.put("annotations",Map.of("readOnlyHint",readOnly,"destructiveHint",!readOnly));if(outputSchema!=null)m.put("outputSchema",outputSchema());return m;}
}
