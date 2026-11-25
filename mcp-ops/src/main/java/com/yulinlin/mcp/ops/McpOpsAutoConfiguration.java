package com.yulinlin.mcp.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.McpAutoConfiguration;
import com.yulinlin.mcp.McpLifecycle;
import com.yulinlin.mcp.McpProperties;
import com.yulinlin.mcp.auth.McpAccessPolicy;
import com.yulinlin.mcp.auth.McpAuthentication;
import com.yulinlin.mcp.management.McpManagementGuard;
import com.yulinlin.mcp.process.ProcessControlService;
import com.yulinlin.mcp.process.ProcessOverviewService;
import com.yulinlin.mcp.store.McpAuditTargetProvider;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.mcp.task.*;
import com.yulinlin.mcp.tool.SystemToolProvider;
import com.yulinlin.security.auth.ISecurityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

/**
 * AI 运维工具：引入 mcp-ops 后，按开关注册
 * {@code yulinlin.mcp.command.enabled}（command_execute、task_*）、{@code process.enabled}（process_*）、{@code monitor.enabled}（server_info）。
 * 命令以应用进程的系统账号执行，等同于给持有令牌的 AI 一个 shell；开关默认全部关闭。
 */
@AutoConfiguration(after = McpAutoConfiguration.class)
@ConditionalOnProperty(prefix = "yulinlin.mcp", name = "enabled", havingValue = "true")
public class McpOpsAutoConfiguration {
    @Bean @ConditionalOnMissingBean(CommandExecutor.class)
    @ConditionalOnProperty(prefix = "yulinlin.mcp.command", name = "enabled", havingValue = "true")
    public CommandExecutor mcpCommandExecutor(McpProperties p) { return new LocalCommandExecutor(p); }

    @Bean @ConditionalOnMissingBean
    public McpTaskOutputResolver mcpTaskOutputResolver(McpProperties p) {
        return id -> p.getDataDirectory().toAbsolutePath().normalize().resolve(p.getApplicationId()).resolve(p.getInstanceId()).resolve("output").resolve(id + ".log");
    }

    @Bean(destroyMethod = "shutdown") @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "yulinlin.mcp.command", name = "enabled", havingValue = "true")
    public McpTasks mcpTasks(McpStore store, McpAuthentication authentication, McpProperties p, CommandExecutor executor,
                             McpAccessPolicy policy, McpTaskOutputResolver outputs) {
        return new McpTasks(store, authentication, p, executor, policy, outputs);
    }

    /** 启动时恢复本实例的任务状态（活跃任务标为 unknown），每小时清理已结束的任务和输出。 */
    @Bean
    @ConditionalOnBean(McpTasks.class)
    public McpLifecycle mcpTaskLifecycle(McpTasks tasks) {
        return new McpLifecycle() {
            @Override public void initialize() { tasks.recover(); }
            @Override public void clean() { tasks.clean(); }
        };
    }

    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "yulinlin.mcp.process", name = "enabled", havingValue = "true")
    public ProcessControlService mcpProcessControl() { return new ProcessControlService(); }

    @Bean @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "yulinlin.mcp.process", name = "enabled", havingValue = "true")
    public ProcessOverviewService mcpProcessOverview(ProcessControlService controls) { return new ProcessOverviewService(controls); }

    @Bean @ConditionalOnMissingBean(SystemToolProvider.class)
    public SystemToolProvider mcpSystemTools(McpProperties p, ObjectProvider<McpTasks> tasks, McpAccessPolicy policy,
                                             ObjectProvider<ProcessOverviewService> processes, ObjectProvider<ProcessControlService> controls,
                                             ObjectMapper json) {
        return new SystemToolProvider(p, tasks.getIfAvailable(), policy, processes.getIfAvailable(), controls.getIfAvailable(), json);
    }

    /** 运维工具调用的审计字段：命令原文、工作目录、任务 id、实例等，与业务的 McpAuditTargetProvider 互相追加。 */
    @Bean
    public McpAuditTargetProvider mcpOpsAuditTargets(McpProperties p) { return new OpsAuditTargets(p); }

    @Bean
    @ConditionalOnBean(McpTasks.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public McpTaskController mcpTaskController(McpTasks tasks, ISecurityManager security, McpAccessPolicy policy, McpManagementGuard guard) {
        return new McpTaskController(tasks, security, policy, guard);
    }
}
