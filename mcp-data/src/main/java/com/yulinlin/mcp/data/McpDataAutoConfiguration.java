package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.data.core.cache.CacheRegistry;
import com.yulinlin.mcp.McpAutoConfiguration;
import com.yulinlin.mcp.McpProperties;
import com.yulinlin.mcp.store.McpStore;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 数据工具（data.*）。配置了 yulinlin.mcp.data.read 或 write 才注册；
 * 支持项目里注册的 JDBC、Elasticsearch 数据源，不要求类路径上有某一种。
 */
@AutoConfiguration(after = McpAutoConfiguration.class)
@ConditionalOnProperty(prefix = "yulinlin.mcp", name = "enabled", havingValue = "true")
@Conditional(McpDataAutoConfiguration.OnDataConfigured.class)
public class McpDataAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public McpDataService mcpDataService(McpProperties properties, McpStore store, ObjectMapper json, ObjectProvider<CacheRegistry> caches) {
        return new McpDataService(properties, store, json, caches::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataToolProvider mcpDataTools(McpDataService data, ObjectMapper json) {
        return new DataToolProvider(data, json);
    }

    /** 数据工具的审计字段（数据源组、SQL / 命令开头、dryRun 等），与业务的 McpAuditTargetProvider 互相追加，不会被替换掉。 */
    @Bean
    public com.yulinlin.mcp.store.McpAuditTargetProvider mcpDataAuditTargets() {
        return DataAuditTargets::of;
    }

    static class OnDataConfigured extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var binder = Binder.get(context.getEnvironment());
            boolean configured = configured(binder, "yulinlin.mcp.data.read") || configured(binder, "yulinlin.mcp.data.write");
            return configured ? ConditionOutcome.match("yulinlin.mcp.data 已配置 read 或 write")
                    : ConditionOutcome.noMatch("yulinlin.mcp.data.read / write 未配置");
        }

        private static boolean configured(Binder binder, String name) {
            return binder.bind(name, Bindable.listOf(String.class)).map(list -> !list.isEmpty()).orElse(false);
        }
    }
}
