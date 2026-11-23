package com.yulinlin.mcp.transport;
import com.yulinlin.mcp.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.security.auth.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import com.yulinlin.security.annotation.*;
import com.yulinlin.security.limit.LimitBy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/** Stateless MCP Streamable HTTP (2025 revisions), JSON responses; GET SSE is intentionally unsupported. */
@RestController
@RequestMapping("${yulinlin.mcp.path:/mcp}")
@ApiPublic @ApiCrypto(false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="yulinlin.mcp",name="enabled",havingValue="true")
public class McpTransport {
    private static final Set<String> VERSIONS=Set.of("2025-03-26","2025-06-18","2025-11-25");
    private final ObjectMapper json;private final McpToolRegistry tools;private final McpCredentials credentials;private final McpAudit audit;private final McpProperties properties;private final McpHttpGuard guard;
    public McpTransport(ObjectMapper json,McpToolRegistry tools,McpCredentials credentials,McpAudit audit,McpProperties properties,McpHttpGuard guard){this.json=json;this.tools=tools;this.credentials=credentials;this.audit=audit;this.properties=properties;this.guard=guard;}
    @PostMapping
    @ApiLimit(count=180,interval="1m",by=LimitBy.IP)
    public ResponseEntity<?> post(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","no-store"); guard.check(request);
        var credential=credentials.authenticate(request);
        var user=credentials.requireValid(credential);
        var context=new McpCallContext(properties.getApplicationId(),credential.id(),user,McpStore.id(),request.getContextPath());
        if(request.getContentType()==null || !request.getContentType().split(";",2)[0].trim().equalsIgnoreCase("application/json"))
            return ResponseEntity.status(415).build();
        String accept=request.getHeader("Accept");
        if(accept!=null && !accept.contains("application/json") && !accept.contains("*/*")) return ResponseEntity.status(406).build();
        if(request.getContentLengthLong()>properties.getMaxRequestBytes()) return ResponseEntity.status(413).build();
        byte[] bytes=request.getInputStream().readNBytes(properties.getMaxRequestBytes()+1);
        if(bytes.length>properties.getMaxRequestBytes()) return ResponseEntity.status(413).build();
        JsonNode body;
        try { body=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(bytes); }
        catch(JsonProcessingException invalid) { return rpcError(null,-32700,"Parse error",400); }
        if(body==null || !body.isObject() || !"2.0".equals(body.path("jsonrpc").asText()) || !body.path("method").isTextual()
                || (body.has("id") && !(body.get("id").isTextual() || body.get("id").isIntegralNumber()))
                || (body.has("params")&&!body.get("params").isObject())) return rpcError(null,-32600,"Invalid Request",400);
        JsonNode id=body.get("id"),params=body.path("params"); String method=body.get("method").asText();
        String protocol=request.getHeader("MCP-Protocol-Version");
        if(protocol!=null&&!VERSIONS.contains(protocol)) return rpcError(id,-32600,"Unsupported MCP-Protocol-Version",400);
        if(id==null) return method.startsWith("notifications/") ? ResponseEntity.accepted().build() : rpcError(null,-32600,"Request id required",400);
        switch(method) {
            case "initialize": {
                if(!params.path("protocolVersion").isTextual() || !params.path("capabilities").isObject() || !params.path("clientInfo").isObject())
                    return rpcError(id,-32602,"Invalid initialize parameters",200);
                String wanted=params.path("protocolVersion").asText();
                if(protocol!=null&&!protocol.equals(wanted)) return rpcError(id,-32602,"Protocol version mismatch",400);
                return result(id,Map.of("protocolVersion",VERSIONS.contains(wanted)?wanted:"2025-11-25",
                        "capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name",properties.getApplicationId(),"version","3.0"),
                        "instructions","工具和数据范围由业务权限控制；命令 cwd 使用服务器绝对路径。命令返回后台任务，使用 task_get/task_output 跟进。避免重复提交变更操作。"));
            }
            case "ping": return result(id,Map.of());
            case "tools/list":
                if(params.has("cursor")) return rpcError(id,-32602,"This tool list has no pagination cursor",200);
                return result(id,Map.of("tools",tools.list(context)));
            case "tools/call": return call(id,params,credential,context);
            default: return rpcError(id,-32601,"Method not found",200);
        }
    }
    private ResponseEntity<?> call(JsonNode id,JsonNode params,McpCredentials.Credential credential,McpCallContext context) {
        if(!params.path("name").isTextual()) return rpcError(id,-32602,"Tool name required",200);
        String name=params.path("name").asText();
        if(!tools.contains(name)) return rpcError(id,-32602,"Unknown tool",200);
        JsonNode args=params.has("arguments")?params.get("arguments"):json.createObjectNode();
        try { tools.validate(context,name,args); } catch(McpException e){return rpcError(id,-32003,e.getMessage(),403);} catch(IllegalArgumentException e){return rpcError(id,-32602,e.getMessage(),200);}
        // Audit starts before side effects. Database failure must not silently permit an unrecorded operation.
        McpAudit.Entry entry;
        try { entry=audit.begin(credential,name,args); }
        catch(RuntimeException unavailable) { return rpcError(id,-32603,"审计存储不可用，未执行工具",500); }
        Object value;
        try { value=SecurityContext.callAs(new LoginSession(null,null,context.user(),"mcp"),()->tools.call(context,name,args)); }
        catch(Exception error) {
            try { audit.finish(entry,false); } catch(RuntimeException ignored) { /* Initial audit still records the attempted call. */ }
            String message=(error instanceof IllegalArgumentException || error instanceof McpException || error instanceof IOException)
                    ? error.getMessage():"工具执行失败，请检查服务器日志及任务状态，勿直接重复提交";
            return result(id,Map.of("content",List.of(Map.of("type","text","text",message==null?"执行失败":message)),"isError",true));
        }
        boolean auditSaved=true;
        try { audit.finish(entry,true); } catch(RuntimeException error) { auditSaved=false; }
        var payload=json.createObjectNode(); payload.set("data",json.valueToTree(value));
        try {tools.validateOutput(name,payload);}catch(RuntimeException invalid){return result(id,Map.of("content",List.of(Map.of("type","text","text","工具输出不符合 Schema；操作可能已完成，请勿自动重试")),"isError",true));}
        if(!auditSaved) payload.put("warning","操作已完成，但审计完成状态保存失败；请勿重复执行");
        var response = new LinkedHashMap<String,Object>();
        response.put("content",List.of(Map.of("type","text","text",payload.toString())));
        response.put("structuredContent",payload); response.put("isError",false);
        return result(id,response);
    }
    @RequestMapping(method={RequestMethod.GET,RequestMethod.DELETE})
    public ResponseEntity<Void> unsupported() { return ResponseEntity.status(405).allow(HttpMethod.POST).build(); }
    private ResponseEntity<?> result(JsonNode id,Object value) {
        var body=json.createObjectNode().put("jsonrpc","2.0"); body.set("id",id); body.set("result",json.valueToTree(value));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
    private ResponseEntity<?> rpcError(JsonNode id,int code,String message,int status) {
        ObjectNode body=json.createObjectNode().put("jsonrpc","2.0"); body.set("id",id==null?NullNode.instance:id);
        body.set("error",json.valueToTree(Map.of("code",code,"message",message)));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
