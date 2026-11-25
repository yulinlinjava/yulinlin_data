package com.yulinlin.mcp.ops;

import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.auth.McpAccessPolicy;
import com.yulinlin.mcp.management.McpManagementGuard;
import com.yulinlin.mcp.task.McpTasks;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.starter.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/** 命令任务的管理接口（网页登录，要求 canManage），挂在管理接口前缀下：/api/mcp/tasks。只在开启命令工具时注册。 */
@RestController @RequestMapping("${yulinlin.mcp.management-path:/api/mcp}/tasks") @ApiLogin @ApiCrypto(false)
public class McpTaskController {
    private final McpTasks tasks;
    private final ISecurityManager security;
    private final McpAccessPolicy policy;
    private final McpManagementGuard guard;

    public McpTaskController(McpTasks tasks, ISecurityManager security, McpAccessPolicy policy, McpManagementGuard guard) {
        this.tasks = tasks;
        this.security = security;
        this.policy = policy;
        this.guard = guard;
    }

    private IUserDetails manage() {
        var user = security.requireUser();
        if (!policy.canManage(user)) throw new McpException(403, "无 MCP 管理权限");
        return user;
    }

    @GetMapping public R<?> list(@RequestParam(defaultValue = "1") int page) { manage(); return R.ok(tasks.list(page)); }

    @GetMapping("/{id}") public R<?> get(@PathVariable String id) { manage(); return R.ok(tasks.get(id)); }

    @GetMapping("/{id}/output")
    public R<?> output(@PathVariable String id, @RequestParam(defaultValue = "0") long offset) throws IOException {
        manage();
        return R.ok(tasks.output(id, offset, 65536));
    }

    @PostMapping("/{id}/cancel")
    public R<?> cancel(@PathVariable String id, HttpServletRequest request) {
        var user = manage();
        guard.checkWrite(request);
        if (!policy.canInvoke(user, "task_cancel")) throw new McpException(403, "无 task_cancel 权限");
        return R.ok(tasks.cancel(id));
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable String id, HttpServletRequest request) throws IOException {
        manage();
        guard.checkWrite(request);
        tasks.delete(id);
        return R.ok();
    }
}
