package com.yulinlin.mcp.management;
import com.yulinlin.mcp.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.token.McpTokenService.*;
import com.yulinlin.mcp.tool.McpToolRegistry;
import com.yulinlin.security.auth.*;
import com.yulinlin.security.annotation.*;
import com.yulinlin.security.limit.LimitBy;
import com.yulinlin.starter.domain.R;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * MCP 管理接口。写操作需要请求头 {@code X-MCP-Action: mcp-management}（见 {@link McpManagementGuard}）。
 * 令牌：所有登录用户可管理自己的令牌；{@link McpAccessPolicy#canManage} 可管理全部、代他人签发、设置 attributes。
 */
@RestController @RequestMapping("${yulinlin.mcp.management-path:/api/mcp}") @ApiLogin @ApiCrypto(false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="yulinlin.mcp",name="enabled",havingValue="true")
public class McpManagementController {
 private final McpTokenService tokens;private final McpAuthentication authentication;private final McpToolRegistry tools;private final McpAuditService audit;private final ISecurityManager security;private final McpAccessPolicy policy;private final McpManagementGuard guard;
 public McpManagementController(McpTokenService tokens,McpAuthentication authentication,McpToolRegistry tools,McpAuditService audit,ISecurityManager security,McpAccessPolicy policy,McpManagementGuard guard){this.tokens=tokens;this.authentication=authentication;this.tools=tools;this.audit=audit;this.security=security;this.policy=policy;this.guard=guard;}

 private IUserDetails manage(){var user=security.requireUser();if(!policy.canManage(user))throw new McpException(403,"无 MCP 管理权限");return user;}
 private Actor actor(HttpServletRequest request){return new Actor(security.requireUser(),request.getRemoteAddr());}
 private Actor writer(HttpServletRequest request){var actor=actor(request);guard.checkWrite(request);return actor;}
 private static void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}

 // ------------------------------------------------------------ 全局

 @GetMapping @ApiLimit(count=60,interval="1m",by=LimitBy.IP)
 public R<?> state(){var user=security.requireUser();return R.ok(Map.of("enabled",authentication.enabled(),"manager",policy.canManage(user),"canUse",policy.canUse(user)));}
 @PutMapping @ApiLimit(count=10,interval="1m",by=LimitBy.IP)
 public R<?> enabled(@RequestBody Enable body,HttpServletRequest request){manage();guard.checkWrite(request);authentication.enable(body.enabled());return R.ok();}
 /** 当前账号有权限的工具，供签发令牌时选择范围。 */
 @GetMapping("/tools") public R<?> tools(){return R.ok(tools.catalog(security.requireUser()));}

 // ------------------------------------------------------------ 令牌

 @GetMapping("/credentials")
 public R<?> list(@RequestParam(required=false)String owner,@RequestParam(required=false)McpTokenStatus status,@RequestParam(required=false)String keyword,
                  @RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,HttpServletRequest request,HttpServletResponse response){
  noStore(response);return R.ok(tokens.list(actor(request),owner,status,keyword,page,size));
 }
 @GetMapping("/credentials/{id}")
 public R<?> get(@PathVariable String id,HttpServletRequest request,HttpServletResponse response){noStore(response);return R.ok(tokens.get(actor(request),id));}
 /** 签发；返回的 token 明文只出现这一次。 */
 @PostMapping("/credentials") @ApiLimit(count=10,interval="1m",by=LimitBy.IP)
 public R<?> issue(@RequestBody IssueRequest body,HttpServletRequest request,HttpServletResponse response){noStore(response);return R.ok(tokens.issue(writer(request),body));}
 @PatchMapping("/credentials/{id}") @ApiLimit(count=30,interval="1m",by=LimitBy.IP)
 public R<?> update(@PathVariable String id,@RequestBody UpdateRequest body,HttpServletRequest request,HttpServletResponse response){noStore(response);return R.ok(tokens.update(writer(request),id,body));}
 /** 重新生成密钥；graceSeconds 内旧令牌仍可用。 */
 @PostMapping("/credentials/{id}/rotate") @ApiLimit(count=10,interval="1m",by=LimitBy.IP)
 public R<?> rotate(@PathVariable String id,@RequestBody(required=false) Rotate body,HttpServletRequest request,HttpServletResponse response){noStore(response);return R.ok(tokens.rotate(writer(request),id,body==null?0:body.graceSeconds()));}
 @PostMapping("/credentials/{id}/disable") public R<?> disable(@PathVariable String id,HttpServletRequest request){return R.ok(tokens.disable(writer(request),id));}
 @PostMapping("/credentials/{id}/enable") public R<?> enable(@PathVariable String id,HttpServletRequest request){return R.ok(tokens.enable(writer(request),id));}
 /** 撤销，不可恢复。 */
 @PostMapping("/credentials/{id}/revoke") public R<?> revoke(@PathVariable String id,HttpServletRequest request){return R.ok(tokens.revoke(writer(request),id));}
 /** 物理删除，仅限已撤销或已过期的令牌。 */
 @DeleteMapping("/credentials/{id}") public R<?> delete(@PathVariable String id,HttpServletRequest request){tokens.delete(writer(request),id);return R.ok();}
 /** 该令牌最近的工具调用记录（扫描最近 1000 条调用审计）。 */
 @GetMapping("/credentials/{id}/usage")
 public R<?> usage(@PathVariable String id,HttpServletRequest request){
  var token=tokens.get(actor(request),id);var result=new ArrayList<McpAudit.Entry>();
  for(int page=1;page<=20&&result.size()<50;page++){var rows=audit.list(page);for(var row:rows)if(token.id().equals(row.credentialId())&&result.size()<50)result.add(row);if(rows.size()<50)break;}
  return R.ok(result);
 }

 // ------------------------------------------------------------ 审计（管理员）；命令任务接口在 mcp-ops 模块

 /** type=call 工具调用审计（默认），type=manage 令牌管理审计。 */
 @GetMapping("/audit") public R<?> audit(@RequestParam(defaultValue="call")String type,@RequestParam(defaultValue="1")int page){
  manage();
  return switch(type){case "call"->R.ok(audit.list(page));case "manage"->R.ok(audit.listManagement(page));default->throw new IllegalArgumentException("type 只能是 call 或 manage");};
 }

 public record Enable(boolean enabled){}
 public record Rotate(long graceSeconds){}
}
