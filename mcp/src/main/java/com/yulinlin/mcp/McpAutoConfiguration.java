package com.yulinlin.mcp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.mcp.task.*;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.process.*;
import com.yulinlin.mcp.transport.*;
import com.yulinlin.mcp.management.*;
import com.yulinlin.security.auth.*;
import org.springframework.beans.factory.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
@AutoConfiguration(after={JacksonAutoConfiguration.class,org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class})
@ConditionalOnProperty(prefix="yulinlin.mcp",name="enabled",havingValue="true")
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {
 @Bean @ConditionalOnMissingBean(McpStore.class) public McpStore mcpStore(ObjectMapper json,McpProperties p){return new OrmMcpStore(json,p);}
 @Bean @ConditionalOnMissingBean(McpTokenStore.class) public McpTokenStore mcpTokenStore(ObjectMapper json,McpProperties p){return new OrmMcpTokenStore(json,p);}
 @Bean @ConditionalOnMissingBean(McpIdentityProvider.class) public McpIdentityProvider mcpIdentityProvider(UserStore users){return users::load;}
 /** 工具注册表懒加载，避免 registry → SystemToolProvider → McpTasks → McpAuthentication → 令牌服务 的循环依赖。 */
 @Bean @ConditionalOnMissingBean public McpTokenService mcpTokenService(McpTokenStore store,McpIdentityProvider identities,McpAccessPolicy policy,ObjectProvider<McpToolRegistry> tools,McpProperties p,ApplicationEventPublisher events){return new McpTokenService(store,identities,policy,tools::getIfAvailable,p.getToken(),events::publishEvent,java.time.Clock.systemUTC());}
 @Bean @ConditionalOnMissingBean(TokenAuthenticator.class) public TokenAuthenticator mcpTokenAuthenticator(McpTokenService tokens){return new TokenAuthenticator(tokens);}
 @Bean @ConditionalOnMissingBean public McpAuthentication mcpAuthentication(ObjectProvider<McpAuthenticator> authenticators,McpStore store,McpIdentityProvider identities,McpAccessPolicy policy){return new McpAuthentication(authenticators.orderedStream().toList(),store,identities,policy);}
 /** 业务可以注册任意多个 McpAuditTargetProvider，按 @Order 依次追加字段；数据工具字段由 McpAudit 固定记录，不会被替换掉。 */
 @Bean @ConditionalOnMissingBean(McpAuditService.class) public McpAudit mcpAudit(McpStore store,McpProperties p,ObjectProvider<McpAuditTargetProvider> targets){
  var providers=targets.orderedStream().toList();
  return new McpAudit(store,p,(tool,args)->{var all=new java.util.LinkedHashMap<String,String>();for(var provider:providers){var m=provider.target(tool,args);if(m!=null)all.putAll(m);}return all;});
 }
 @Bean @ConditionalOnMissingBean public McpTokenAuditListener mcpTokenAuditListener(McpAuditService audit){return new McpTokenAuditListener(audit);}
 @Bean @ConditionalOnMissingBean public McpToolRegistry mcpToolRegistry(ObjectProvider<McpToolProvider> providers,McpAccessPolicy policy){return new McpToolRegistry(providers.orderedStream().toList(),policy);}
 @Bean @ConditionalOnMissingBean(jakarta.validation.Validator.class) public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean mcpValidator(){return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();}
 @Bean public AnnotatedMcpTools annotatedMcpTools(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beans,ObjectMapper json,jakarta.validation.Validator validator,McpToolRegistry registry){return new AnnotatedMcpTools(beans,json,validator,registry);}
 @Bean @ConditionalOnMissingBean public McpHttpGuard mcpHttpGuard(McpProperties p){return new McpHttpGuard(p);}
 @Bean @ConditionalOnMissingBean(McpManagementGuard.class) public McpManagementGuard mcpManagementGuard(McpHttpGuard guard){return request->{guard.check(request);if(!"mcp-management".equals(request.getHeader("X-MCP-Action")))throw new McpException(403,"缺少 MCP 管理请求标识");};}
 @Bean @ConditionalOnMissingBean(CommandExecutor.class) @ConditionalOnProperty(prefix="yulinlin.mcp.command",name="enabled",havingValue="true") public CommandExecutor mcpCommandExecutor(McpProperties p){return new LocalCommandExecutor(p);}
 @Bean @ConditionalOnMissingBean public McpTaskOutputResolver mcpTaskOutputResolver(McpProperties p){return id->p.getDataDirectory().toAbsolutePath().normalize().resolve(p.getApplicationId()).resolve(p.getInstanceId()).resolve("output").resolve(id+".log");}
 @Bean(destroyMethod="shutdown") @ConditionalOnMissingBean @ConditionalOnProperty(prefix="yulinlin.mcp.command",name="enabled",havingValue="true") public McpTasks mcpTasks(McpStore store,McpAuthentication authentication,McpProperties p,CommandExecutor executor,McpAccessPolicy policy,McpTaskOutputResolver outputs){return new McpTasks(store,authentication,p,executor,policy,outputs);}
 @Bean @ConditionalOnMissingBean @ConditionalOnProperty(prefix="yulinlin.mcp.process",name="enabled",havingValue="true") public ProcessControlService mcpProcessControl(){return new ProcessControlService();}
 @Bean @ConditionalOnMissingBean @ConditionalOnProperty(prefix="yulinlin.mcp.process",name="enabled",havingValue="true") public ProcessOverviewService mcpProcessOverview(ProcessControlService controls){return new ProcessOverviewService(controls);}
 @Bean @ConditionalOnMissingBean(SystemToolProvider.class) public SystemToolProvider mcpSystemTools(McpProperties p,ObjectProvider<McpTasks> tasks,McpAccessPolicy policy,ObjectProvider<ProcessOverviewService> processes,ObjectProvider<ProcessControlService> controls,ObjectMapper json){return new SystemToolProvider(p,tasks.getIfAvailable(),policy,processes.getIfAvailable(),controls.getIfAvailable(),json);}
 @Bean public SmartInitializingSingleton mcpInitialize(McpProperties p,McpStore store,McpTokenStore tokens,ObjectProvider<McpTasks> tasks,ObjectProvider<McpLifecycle> lifecycles,McpMaintenance maintenance){return ()->{p.validate();store.initialize();tokens.initialize();lifecycles.orderedStream().forEach(McpLifecycle::initialize);tasks.ifAvailable(McpTasks::recover);maintenance.start();};}
 @Bean(destroyMethod="close") public McpMaintenance mcpMaintenance(McpAuditService audit,ObjectProvider<McpTasks> tasks,ObjectProvider<McpLifecycle> lifecycles){return new McpMaintenance(audit,tasks.getIfAvailable(),lifecycles.orderedStream().toList());}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpTransport mcpTransport(ObjectMapper json,McpToolRegistry tools,McpAuthentication authentication,McpAuditService audit,McpProperties p,McpHttpGuard guard){return new McpTransport(json,tools,authentication,audit,p,guard);}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpManagementController mcpManagement(McpTokenService tokens,McpAuthentication authentication,McpToolRegistry tools,ObjectProvider<McpTasks> tasks,McpAuditService audit,ISecurityManager security,McpAccessPolicy policy,McpManagementGuard guard){return new McpManagementController(tokens,authentication,tools,tasks,audit,security,policy,guard);}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpExceptionAdvice mcpExceptionAdvice(){return new McpExceptionAdvice();}
}
