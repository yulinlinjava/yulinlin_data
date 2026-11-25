package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.*;
import com.yulinlin.mcp.store.McpStore;
import com.yulinlin.mcp.token.*;
import com.yulinlin.mcp.tool.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class McpConfigurationTest extends McpTestSupport {
    final ApplicationContextRunner base = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class))
        .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> JSON);
    /** 内存 McpStore，令牌存储由调用方提供。 */
    ApplicationContextRunner enabledWithoutTokenStore() {
        return base.withPropertyValues("yulinlin.mcp.enabled=true","yulinlin.mcp.application-id=test")
            .withBean(McpStore.class,MemoryStore::new).withBean(McpIdentityProvider.class,()->id->ADMIN);
    }
    ApplicationContextRunner enabled() {
        return enabledWithoutTokenStore().withBean(McpTokenStore.class,MemoryTokenStore::new);
    }
    @Test void disabledModuleRegistersNoMcpServices() {
        base.run(c->{assertThat(c).hasNotFailed().doesNotHaveBean(McpTokenService.class).doesNotHaveBean(McpAuthentication.class)
            .doesNotHaveBean(McpMaintenance.class);});
    }
    @Test void customStoreIsUsedAndOptionalSystemToolsRemainOff() {
        enabled().withBean(McpAccessPolicy.class,Policy::new).run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(McpStore.class)
                .hasSingleBean(TokenAuthenticator.class).hasSingleBean(McpTokenAuditListener.class);
            assertThat(c.getBean(McpStore.class)).isInstanceOf(MemoryStore.class);
            assertThat(c.getBean(McpToolRegistry.class).list(ctx("id"))).isEmpty();
            assertThat(c.getBean(McpAuthentication.class).enabled()).isFalse();
        });
    }
    @Test void missingPolicyFailsInsteadOfGrantingAnonymousAccess() {
        enabled().run(c->assertThat(c).hasFailed());
    }
    /** 核心模块自己不带任何工具：没引入 mcp-ops 时，即使打开命令开关也不会出现命令工具。 */
    @Test void coreRegistersNoToolsEvenWhenOpsSwitchesAreOn() {
        enabled().withBean(McpAccessPolicy.class,Policy::new)
            .withPropertyValues("yulinlin.mcp.command.enabled=true","yulinlin.mcp.process.enabled=true","yulinlin.mcp.monitor.enabled=true")
            .run(c->{
                assertThat(c).hasNotFailed();
                assertThat(c.getBean(McpToolRegistry.class).list(ctx("id"))).isEmpty();
            });
    }
    @Test void invalidInstanceIdentityFailsStartup() {
        enabled().withBean(McpAccessPolicy.class,Policy::new)
            .withPropertyValues("yulinlin.mcp.instance-id=../escape")
            .run(c->assertThat(c).hasFailed());
    }
    @Test void invalidTokenLimitsFailStartup() {
        enabled().withBean(McpAccessPolicy.class,Policy::new)
            .withPropertyValues("yulinlin.mcp.token.cache-seconds=-1")
            .run(c->assertThat(c).hasFailed());
    }
}
