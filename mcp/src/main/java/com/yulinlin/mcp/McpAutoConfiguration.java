package com.yulinlin.mcp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.mcp.task.*;
import com.yulinlin.mcp.process.*;
import com.yulinlin.mcp.transport.*;
import com.yulinlin.mcp.management.*;
import com.yulinlin.security.auth.*;
import org.springframework.beans.factory.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
@AutoConfiguration(after={JacksonAutoConfiguration.class,org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class})
@ConditionalOnProperty(prefix="yulinlin.mcp",name="enabled",havingValue="true")
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {
 @Bean @ConditionalOnMissingBean(McpStore.class) public McpStore mcpStore(ObjectMapper json,McpProperties p){return new OrmMcpStore(json,p);}
 @Bean @ConditionalOnMissingBean(McpIdentityProvider.class) public McpIdentityProvider mcpIdentityProvider(UserStore users){return users::load;}
 @Bean @ConditionalOnMissingBean public McpCredentials mcpCredentials(McpStore store,McpIdentityProvider identities,McpAccessPolicy policy){return new McpCredentials(store,identities,policy);}
 @Bean @ConditionalOnMissingBean public McpAuditTargetProvider mcpAuditTargetProvider(){return (tool,args)->java.util.Map.of();}
 @Bean @ConditionalOnMissingBean public McpAudit mcpAudit(McpStore store,McpProperties p,McpAuditTargetProvider targets){return new McpAudit(store,p,targets);}
 @Bean @ConditionalOnMissingBean public McpToolRegistry mcpToolRegistry(ObjectProvider<McpToolProvider> providers,McpAccessPolicy policy){return new McpToolRegistry(providers.orderedStream().toList(),policy);}
 @Bean @ConditionalOnMissingBean(jakarta.validation.Validator.class) public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean mcpValidator(){return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();}
 @Bean public AnnotatedMcpTools annotatedMcpTools(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beans,ObjectMapper json,jakarta.validation.Validator validator,McpToolRegistry registry){return new AnnotatedMcpTools(beans,json,validator,registry);}
 @Bean @ConditionalOnMissingBean public McpHttpGuard mcpHttpGuard(McpProperties p){return new McpHttpGuard(p);}
 @Bean @ConditionalOnMissingBean(McpManagementGuard.class) public McpManagementGuard mcpManagementGuard(McpHttpGuard guard){return request->{guard.check(request);if(!"mcp-management".equals(request.getHeader("X-MCP-Action")))throw new McpException(403,"缺少 MCP 管理请求标识");};}
 @Bean @ConditionalOnMissingBean(CommandExecutor.class) @ConditionalOnProperty(prefix="yulinlin.mcp.command",name="enabled",havingValue="true") public CommandExecutor mcpCommandExecutor(McpProperties p){return new LocalCommandExecutor(p);}
 @Bean @ConditionalOnMissingBean public McpTaskOutputResolver mcpTaskOutputResolver(McpProperties p){return id->p.getDataDirectory().toAbsolutePath().normalize().resolve(p.getApplicationId()).resolve(p.getInstanceId()).resolve("output").resolve(id+".log");}
 @Bean(destroyMethod="shutdown") @ConditionalOnMissingBean @ConditionalOnProperty(prefix="yulinlin.mcp.command",name="enabled",havingValue="true") public McpTasks mcpTasks(McpStore store,McpCredentials c,McpProperties p,CommandExecutor executor,McpAccessPolicy policy,McpTaskOutputResolver outputs){return new McpTasks(store,c,p,executor,policy,outputs);}
 @Bean @ConditionalOnMissingBean @ConditionalOnProperty(prefix="yulinlin.mcp.process",name="enabled",havingValue="true") public ProcessControlService mcpProcessControl(){return new ProcessControlService();}
 @Bean @ConditionalOnMissingBean @ConditionalOnProperty(prefix="yulinlin.mcp.process",name="enabled",havingValue="true") public ProcessOverviewService mcpProcessOverview(ProcessControlService controls){return new ProcessOverviewService(controls);}
 @Bean @ConditionalOnMissingBean(SystemToolProvider.class) public SystemToolProvider mcpSystemTools(McpProperties p,ObjectProvider<McpTasks> tasks,McpCredentials c,McpAccessPolicy policy,ObjectProvider<ProcessOverviewService> processes,ObjectProvider<ProcessControlService> controls,ObjectMapper json){return new SystemToolProvider(p,tasks.getIfAvailable(),c,policy,processes.getIfAvailable(),controls.getIfAvailable(),json);}
 @Bean public SmartInitializingSingleton mcpInitialize(McpProperties p,McpStore store,ObjectProvider<McpTasks> tasks,McpMaintenance maintenance){return ()->{p.validate();store.initialize();tasks.ifAvailable(McpTasks::recover);maintenance.start();};}
 @Bean(destroyMethod="close") public McpMaintenance mcpMaintenance(McpAudit audit,ObjectProvider<McpTasks> tasks){return new McpMaintenance(audit,tasks.getIfAvailable());}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpTransport mcpTransport(ObjectMapper json,McpToolRegistry tools,McpCredentials c,McpAudit audit,McpProperties p,McpHttpGuard guard){return new McpTransport(json,tools,c,audit,p,guard);}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpManagementController mcpManagement(McpCredentials c,ObjectProvider<McpTasks> tasks,McpAudit audit,ISecurityManager security,McpAccessPolicy policy,McpManagementGuard guard){return new McpManagementController(c,tasks,audit,security,policy,guard);}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpExceptionAdvice mcpExceptionAdvice(){return new McpExceptionAdvice();}
}

