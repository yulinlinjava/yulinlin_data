package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.ops.McpOpsAutoConfiguration;
import com.yulinlin.mcp.store.McpAuditTargetProvider;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.mcp.task.CommandExecutor;
import com.yulinlin.mcp.task.McpTasks;
import com.yulinlin.mcp.token.McpTokenStore;
import com.yulinlin.mcp.tool.McpToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class McpOpsConfigurationTest extends McpTestSupport {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class, McpOpsAutoConfiguration.class))
        .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> JSON)
        .withPropertyValues("yulinlin.mcp.enabled=true", "yulinlin.mcp.application-id=test")
        .withBean(McpStore.class, MemoryStore::new).withBean(McpTokenStore.class, MemoryTokenStore::new)
        .withBean(McpIdentityProvider.class, () -> id -> ADMIN).withBean(McpAccessPolicy.class, Policy::new);

    @Test void opsModuleStillNeedsSwitches() {
        runner.run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(CommandExecutor.class).doesNotHaveBean(McpTasks.class);
            assertThat(c.getBean(McpToolRegistry.class).list(ctx("id"))).isEmpty();
        });
    }

    @Test void allOpsToolsRegisterAndCompileSchemasWithoutStartingCommands() {
        runner.withBean(CommandExecutor.class, () -> (command, directory) -> { throw new AssertionError("must not execute during startup"); })
            .withPropertyValues("yulinlin.mcp.command.enabled=true", "yulinlin.mcp.process.enabled=true", "yulinlin.mcp.monitor.enabled=true")
            .run(c -> {
                assertThat(c).hasNotFailed().hasSingleBean(McpTasks.class);
                var registry = c.getBean(McpToolRegistry.class);
                assertThat(registry.list(ctx("id"))).extracting(t -> t.get("name"))
                    .containsExactly("server_info", "command_execute", "task_list", "task_get", "task_output", "task_cancel", "process_list", "process_stop");
                assertThat(String.join("\n", registry.instructions(ctx("id")))).contains("command_execute", "task_get", "stopIdentity");
                assertThat(c.getBeansOfType(McpAuditTargetProvider.class)).isNotEmpty();
            });
    }
}
