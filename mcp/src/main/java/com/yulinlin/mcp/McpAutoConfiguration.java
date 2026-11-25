package com.yulinlin.mcp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.*;
import com.yulinlin.mcp.tool.*;
import com.yulinlin.mcp.token.*;
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
 /** 工具注册表懒加载，避免 registry → 工具 Provider（如 mcp-ops 的命令工具）→ 任务服务 → McpAuthentication → 令牌服务 的循环依赖。 */
 @Bean @ConditionalOnMissingBean public McpTokenService mcpTokenService(McpTokenStore store,McpIdentityProvider identities,McpAccessPolicy policy,ObjectProvider<McpToolRegistry> tools,McpProperties p,ApplicationEventPublisher events){return new McpTokenService(store,identities,policy,tools::getIfAvailable,p.getToken(),events::publishEvent,java.time.Clock.systemUTC());}
 @Bean @ConditionalOnMissingBean(TokenAuthenticator.class) public TokenAuthenticator mcpTokenAuthenticator(McpTokenService tokens){return new TokenAuthenticator(tokens);}
 @Bean @ConditionalOnMissingBean public McpAuthentication mcpAuthentication(ObjectProvider<McpAuthenticator> authenticators,McpStore store,McpIdentityProvider identities,McpAccessPolicy policy){return new McpAuthentication(authenticators.orderedStream().toList(),store,identities,policy);}
 /** 可选模块和业务都可以注册 McpAuditTargetProvider，按 @Order 依次追加字段，互不替换。 */
 @Bean @ConditionalOnMissingBean(McpAuditService.class) public McpAudit mcpAudit(McpStore store,McpProperties p,ObjectProvider<McpAuditTargetProvider> targets){return new McpAudit(store,p,targets.orderedStream().toList());}
 @Bean @ConditionalOnMissingBean public McpTokenAuditListener mcpTokenAuditListener(McpAuditService audit){return new McpTokenAuditListener(audit);}
 @Bean @ConditionalOnMissingBean public McpToolRegistry mcpToolRegistry(ObjectProvider<McpToolProvider> providers,McpAccessPolicy policy){return new McpToolRegistry(providers.orderedStream().toList(),policy);}
 @Bean @ConditionalOnMissingBean(jakarta.validation.Validator.class) public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean mcpValidator(){return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();}
 @Bean public AnnotatedMcpTools annotatedMcpTools(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beans,ObjectMapper json,jakarta.validation.Validator validator,McpToolRegistry registry){return new AnnotatedMcpTools(beans,json,validator,registry);}
 @Bean @ConditionalOnMissingBean public McpHttpGuard mcpHttpGuard(McpProperties p){return new McpHttpGuard(p);}
 @Bean @ConditionalOnMissingBean(McpManagementGuard.class) public McpManagementGuard mcpManagementGuard(McpHttpGuard guard){return request->{guard.check(request);if(!"mcp-management".equals(request.getHeader("X-MCP-Action")))throw new McpException(403,"缺少 MCP 管理请求标识");};}
 /** 可选模块（mcp-ops 的命令任务恢复、mcp-data 的数据源校验等）通过 McpLifecycle 参与启动和每小时清理。 */
 @Bean public SmartInitializingSingleton mcpInitialize(McpProperties p,McpStore store,McpTokenStore tokens,ObjectProvider<McpLifecycle> lifecycles,McpMaintenance maintenance){return ()->{p.validate();store.initialize();tokens.initialize();lifecycles.orderedStream().forEach(McpLifecycle::initialize);maintenance.start();};}
 @Bean(destroyMethod="close") public McpMaintenance mcpMaintenance(McpAuditService audit,ObjectProvider<McpLifecycle> lifecycles){return new McpMaintenance(audit,lifecycles.orderedStream().toList());}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpTransport mcpTransport(ObjectMapper json,McpToolRegistry tools,McpAuthentication authentication,McpAuditService audit,McpProperties p,McpHttpGuard guard){return new McpTransport(json,tools,authentication,audit,p,guard);}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpManagementController mcpManagement(McpTokenService tokens,McpAuthentication authentication,McpToolRegistry tools,McpAuditService audit,ISecurityManager security,McpAccessPolicy policy,McpManagementGuard guard){return new McpManagementController(tokens,authentication,tools,audit,security,policy,guard);}
 @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET) @ConditionalOnMissingBean public McpExceptionAdvice mcpExceptionAdvice(){return new McpExceptionAdvice();}
}
