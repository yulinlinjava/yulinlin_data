package com.yulinlin.mcp.management;
import com.yulinlin.mcp.*;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.task.McpTasks;
import com.yulinlin.security.auth.*;
import com.yulinlin.security.annotation.*;
import com.yulinlin.security.limit.LimitBy;
import com.yulinlin.starter.domain.R;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;
@RestController @RequestMapping("${yulinlin.mcp.management-path:/api/mcp}") @ApiLogin @ApiCrypto(false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="yulinlin.mcp",name="enabled",havingValue="true")
public class McpManagementController {
 private final McpCredentials credentials;private final McpTasks tasks;private final McpAudit audit;private final ISecurityManager security;private final McpAccessPolicy policy;private final McpManagementGuard guard;
 public McpManagementController(McpCredentials credentials,ObjectProvider<McpTasks> tasks,McpAudit audit,ISecurityManager security,McpAccessPolicy policy,McpManagementGuard guard){this.credentials=credentials;this.tasks=tasks.getIfAvailable();this.audit=audit;this.security=security;this.policy=policy;this.guard=guard;}
 private IUserDetails manage(){var user=security.requireUser();if(!policy.canManage(user))throw new McpException(403,"无 MCP 管理权限");return user;}
 private IUserDetails write(HttpServletRequest request){var user=manage();guard.checkWrite(request);return user;}
 private McpTasks tasks(){if(tasks==null)throw new McpException(404,"命令工具未开启");return tasks;}
 @GetMapping @ApiLimit(count=60,interval="1m",by=LimitBy.IP)
 public R<?> state(HttpServletResponse response){manage();response.setHeader("Cache-Control","no-store");return R.ok(Map.of("enabled",credentials.enabled(),"available",credentials.enabled(),"credentials",credentials.list()));}
 @PutMapping @ApiLimit(count=10,interval="1m",by=LimitBy.IP)
 public R<?> enabled(@RequestBody Enable body,HttpServletRequest request){write(request);credentials.enable(body.enabled());return R.ok();}
 @PostMapping("/credentials") @ApiLimit(count=5,interval="1m",by=LimitBy.IP)
 public R<?> create(@RequestBody Create body,HttpServletRequest request,HttpServletResponse response){var user=write(request);response.setHeader("Cache-Control","no-store");return R.ok(credentials.create(user,body.name(),body.expiresAt()));}
 @DeleteMapping("/credentials/{id}") public R<?> revoke(@PathVariable String id,HttpServletRequest request){write(request);credentials.revoke(id);return R.ok();}
 @GetMapping("/tasks") public R<?> tasks(@RequestParam(defaultValue="1")int page){manage();return R.ok(tasks().list(page));}
 @GetMapping("/tasks/{id}") public R<?> task(@PathVariable String id){manage();return R.ok(tasks().get(id));}
 @GetMapping("/tasks/{id}/output") public R<?> output(@PathVariable String id,@RequestParam(defaultValue="0")long offset)throws IOException{manage();return R.ok(tasks().output(id,offset,65536));}
 @PostMapping("/tasks/{id}/cancel") public R<?> cancel(@PathVariable String id,HttpServletRequest request){var user=write(request);if(!policy.canInvoke(user,"task_cancel"))throw new McpException(403,"无 task_cancel 权限");return R.ok(tasks().cancel(id));}
 @DeleteMapping("/tasks/{id}") public R<?> delete(@PathVariable String id,HttpServletRequest request)throws IOException{write(request);tasks().delete(id);return R.ok();}
 @GetMapping("/audit") public R<?> audit(@RequestParam(defaultValue="1")int page){manage();return R.ok(audit.list(page));}
 public record Enable(boolean enabled){} public record Create(String name,Long expiresAt){public Create(String name){this(name,null);}}
}
