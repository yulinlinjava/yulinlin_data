package com.yulinlin.mcp.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.auth.McpAccessPolicy;
import com.yulinlin.mcp.data.McpDataAutoConfiguration;
import com.yulinlin.mcp.data.McpDataService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 引入 mcp-script 依赖即启用 data.analyze；前提是数据工具已启用（配置了 yulinlin.mcp.data.read 或 write）。 */
@AutoConfiguration(after = McpDataAutoConfiguration.class)
@ConditionalOnBean(McpDataService.class)
public class McpScriptAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ScriptSandbox mcpScriptSandbox(ObjectMapper json) {
        return new ScriptSandbox(json);
    }

    @Bean
    @ConditionalOnMissingBean
    public AnalyzeToolProvider mcpAnalyzeTool(McpDataService data, ScriptSandbox sandbox, McpAccessPolicy policy, ObjectMapper json) {
        return new AnalyzeToolProvider(data, sandbox, policy, json);
    }
}
