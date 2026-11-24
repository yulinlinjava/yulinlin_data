# MCP 快速接入与扩展

[文档首页](../README.md) / [MCP](README.md)

## 依赖和启动

沿用 JDK 25、Spring Boot 3.5.16 和框架 3.0，不为 MCP 升级整个应用。依赖来自同一份源码构建或团队制品仓库。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mcp</artifactId>
    <version>3.0</version>
</dependency>
```

模块依赖公共 `security`、`starter`，不选择 JWT、Sa-Token 等具体认证实现。应用继续选择自己的安全模块并提供 `UserStore`。默认持久化需要一个已经接入的 ORM 数据源；也可以实现 `McpStore`。缺少权限策略、用户加载或存储，启用时启动失败。

```yaml
yulinlin:
  mcp:
    enabled: true
    application-id: my-service
    group: h2  # 已配置的数据源名称；单数据源可省略
```

`enabled` 只控制模块装配。新数据库中的运行开关默认关闭，需要网页登录的管理员通过管理接口启用。详见[配置与管理](configuration.md)。

若使用 Spring Security 原生过滤器链，需要在现有链中允许 `/mcp` 到达 MVC，并只对这个 Bearer 入口忽略浏览器 CSRF 校验；认证由 MCP 入口完成。`@ApiPublic` 不会自动改写原生过滤器规则。例如在已有配置中加入 `requestMatchers("/mcp").permitAll()` 和 `csrf.ignoringRequestMatchers("/mcp")`。不要对 `/api/mcp/**` 放开网页登录或 CSRF 防护。项目自定义的认证 Filter 也不能提前吞掉 MCP 的 Bearer 头。

## 一个权限接口，三个明确的判断

```java
import com.yulinlin.mcp.auth.McpAccessPolicy;
import com.yulinlin.security.auth.IUserDetails;
import org.springframework.context.annotation.Bean;

@Bean
McpAccessPolicy mcpAccessPolicy() {
    return new McpAccessPolicy() {
        @Override public boolean canUse(IUserDetails user) {
            return user.hasPermission("mcp:use");
        }
        @Override public boolean canManage(IUserDetails user) {
            return user.hasPermission("mcp:manage");
        }
        @Override public boolean canInvoke(IUserDetails user, String toolName) {
            return user.hasPermission("mcp:tool:" + toolName);
        }
    };
}
```

权限名称完全由业务约定，框架没有默认放行或超级管理员特判：

| 判断 | 生效位置 |
| --- | --- |
| `canUse` | 创建自己的凭证、每次外部请求校验、工具调用 |
| `canManage` | 网页管理接口、跨凭证任务查看权限 |
| `canInvoke` | 工具列表过滤和实际执行前再次校验；管理页取消任务还需要 `task_cancel` 权限 |

管理权限不会自动赋予工具权限。仅有管理权限的用户可暂停 MCP、撤销凭证、查看管理记录，但不能给自己创建可用凭证或执行命令。新凭证绑定当前管理用户；首版不支持管理员替其他账号创建凭证。

`tools/list` 只返回当前账号通过 `canUse(user)` 和 `canInvoke(user, toolName)` 的工具，无权限的工具不会暴露其定义。即使客户端知道工具名称并直接请求 `tools/call`，执行前仍会再次检查权限，不能通过跳过列表绕过授权。注解注册和显式 Provider 注册的工具使用相同规则。

当前工具权限来自 **Token 所属账号**，没有 Token 独立的工具授权范围（scope/白名单）。同一账号创建的多个有效、未撤销 Token，在相同账号权限下拥有相同的工具调用权限；有效期和撤销状态分别管理。不能用同一账号的两个 Token 配置“一枚只读、一枚可执行命令”。任务数据访问仍按凭证归属检查，并不因工具权限相同而自动共享。

`canInvoke` 只决定是否能调用工具。订单归属、租户、目录范围等数据权限仍由业务服务判断。工具回调在当前已认证用户的 `SecurityContext` 中运行，可继续调用已有 Spring Service 代理；同步调用结束或抛异常后恢复原身份，不创建网页登录会话，也不向其他线程继承身份。

## 注解注册业务工具

普通业务工具无需手写输入或输出 JSON Schema。给 Spring 单例 Bean 的 public 方法添加 `@McpTool`，用 Java DTO 定义参数和返回值，用 `@Schema` 描述业务字段：

```java
import com.yulinlin.mcp.tool.McpTool;
import com.yulinlin.security.auth.SecurityContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {
    private final OrderService orders;

    public OrderTools(OrderService orders) {
        this.orders = orders;
    }

    @McpTool(name = "order_query", description = "按编号查询当前用户有权查看的订单", readOnly = true)
    public OrderResult query(OrderRequest request) {
        // 沿用业务权限检查；工具权限不代替数据归属判断。
        return orders.findVisible(SecurityContext.getUserId(), request.id());
    }

    @Schema(description = "订单查询参数")
    public record OrderRequest(
        @NotBlank
        @Schema(description = "业务订单编号，不是数据库内部行号", example = "order-123")
        String id
    ) {}

    @Schema(description = "订单查询结果")
    public record OrderResult(
        @Schema(description = "业务订单编号") String id,
        @Schema(description = "pending 待支付；paid 已支付；closed 已关闭",
                allowableValues = {"pending", "paid", "closed"}) String status
    ) {}
}
```

示例中的 `OrderService.findVisible` 返回 `OrderResult`，实际应用替换为自己的服务。工具参数直接是 `{"id":"order-123"}`，不会再包一层 `request`；工具方法返回 `OrderResult`，框架自动包装成 `structuredContent.data`，不要再手动返回 `R<OrderResult>` 叠加包装。

调用链为：启动时发现注解并生成/校验 Schema → `tools/list` 发布定义 → 调用时校验输入 Schema → Jackson 转换 DTO → Bean Validation → 调用原 Spring Bean 代理 → 校验输出 Schema。生成后的定义保存在工具注册表，不会每次请求重新反射 DTO。注解工具和显式 Provider 共用权限检查、审计和返回协议，重名工具启动报错。

### 支持范围

| 来源 | 自动处理 |
| --- | --- |
| `@McpTool` | 工具名、工具用途、只读提示；`readOnly` 默认 false |
| 方法签名 | 无参或一个 DTO 参数；具体返回 DTO、基础类型、枚举、集合、数组或 void |
| 普通 JavaBean / record | 按 Jackson 可读写属性生成字段，兼容 getter/setter 与 record 构造参数 |
| 嵌套/递归 DTO、`List<Item>`、`Page<Item>` | 保留具体泛型，支持嵌套或返回的 `Map<String, 明确值类型>`，生成嵌套结构或本地 `$defs`/`$ref`，也支持已绑定类型的泛型父类方法 |
| `@JsonProperty`、`@JsonIgnore` | 复用 JSON 字段名称和忽略规则；命名策略跟随应用 ObjectMapper |
| `@Schema` | description、title、example/examples、defaultValue、allowableValues、required/requiredMode、nullable、数值上下限及排他边界、字符串长度、pattern、format、deprecated |
| Bean Validation | 请求 DTO 自动校验，无需在整个方法上再加 `@Validated`；嵌套校验仍需 `@Valid` |

`@NotNull`、`@NotBlank`、`@NotEmpty` 会生成必填规则；`@Size`、`@Min`、`@Max`、`@DecimalMin`、`@DecimalMax`、正负值约束、布尔断言等映射为 Schema 约束。使用 Default 校验组；其他分组不会被自动当作默认约束。自定义约束、容器元素约束以及 Java 正则的完整语义由 Bean Validation 执行，不承诺全部转换成 JSON Schema。

引用类型字段默认允许 null，基础类型不允许 null；`@NotNull`/`@NotBlank`/`@NotEmpty` 优先要求非空。字段是否必填与是否允许 null 分别表示“必须出现”和“出现后可用什么值”。`@Schema(defaultValue=...)` 仅发布默认值说明，不自动赋值；需要在 DTO/业务方法中实现对应默认行为。字段语义仍需业务填写 `description`，框架不会猜测业务含义。

调用通过 Spring 代理执行，保留事务、业务拦截器等。JDK 动态代理要求工具方法可通过其接口调用；实现类新增但不在接口上的方法会在启动时提示。注解发现限 Spring 单例 Bean，不自动暴露 Controller 或 Service 的所有方法，也不自动注册 prototype/request/session scope 工具。

首版仅自动解析明确、普通的 JSON DTO：不支持动态 `Object`、原始/值类型不明的 Map、非 String 键 Map、JsonNode、原始集合、未绑定泛型、抽象/多态 DTO、`Optional`、异步返回、`byte[]`、日期及其他特殊 Java 类型的自动转换。日期可先用明确格式的 String 或毫秒 Long DTO 字段表示；异步操作可返回业务任务 DTO。自定义 JSON 序列化、展开字段和复杂 OpenAPI Schema 组合请使用下方显式 Provider。已识别的无法推断类型或不支持的 `@Schema` 属性会启动报错，不静默发布任意结构。

## 显式注册业务工具

以下入口保留给动态结构、特殊序列化或需要自行提供完整 Schema 的场景；与 `@McpTool` 同名时会报错，应二选一。

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.tool.McpToolDefinition;
import com.yulinlin.mcp.tool.McpToolProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;

// 示例约定：OrderService.findVisible 返回 OrderView，并检查当前用户的数据权限。
record OrderView(String id, String status) { }

@Bean
McpToolProvider orderTools(ObjectMapper json, OrderService orders) throws Exception {
    var schema = json.readTree("""
        {
          "type": "object",
          "description": "订单查询参数。",
          "properties": {
            "id": {"type": "string", "description": "业务订单编号，不是数据库内部行号。", "minLength": 1, "maxLength": 64, "examples": ["order-123"]}
          },
          "required": ["id"],
          "additionalProperties": false
        }
        """);
    var output = json.readTree("""
        {
          "type": "object",
          "description": "订单查询成功结果，实际订单在 data 中。",
          "properties": {
            "data": {
              "type": "object",
              "description": "当前用户有权查看的订单；无权查看或不存在时由服务抛出业务异常。",
              "properties": {
                "id": {"type": "string", "description": "业务订单编号。"},
                "status": {"type": "string", "description": "订单状态：pending 待支付，paid 已支付，closed 已关闭。", "enum": ["pending", "paid", "closed"]}
              },
              "required": ["id", "status"],
              "additionalProperties": false
            },
            "warning": {"type": "string", "description": "可选完成警告；操作可能已成功，不要自动重复执行。"}
          },
          "required": ["data"],
          "additionalProperties": false
        }
        """);
    return () -> List.of(new McpToolDefinition(
        "order_query", "按订单编号查询当前用户可以访问的订单", schema, output, true,
        (context, args) -> orders.findVisible(context.user().getUserId(), args.get("id").asText())
    ));
}
```

`McpCallContext` 包含 `applicationId`、`credentialId`、当前用户、服务端 `requestId` 和 `contextPath`，不传凭证明文。这里的追踪 `requestId` 与 `command_execute` 参数里的幂等编号不同。

工具名重复启动报错。参数通过 JSON Schema Draft 2020-12 校验，支持嵌套、数组、`$defs`/本地 `$ref`、组合条件等；不自动加载远程 Schema。未知字段是否拒绝由 Schema 的 `additionalProperties` 决定，内置工具均为 `false`。`readOnly` 是工具描述，不会代替服务端授权。

可用完整构造器传入 `outputSchema`。为兼容已有客户端，返回值包装为 `structuredContent.data`，因此输出 Schema 应描述完整的 `{ "data": ... }` 对象。输出校验发生在业务执行之后，失败不能表示业务已回滚，客户端不可自动重试写操作。

### AI 如何获取字段说明

外部 AI 通过 `tools/list` 获取工具的 `name`、`description`、`inputSchema`、`outputSchema` 和只读提示；不需要也不能假定它能读取本地 Markdown、Java 注释或 Swagger 页面。`McpToolDefinition` 会将提供的 Schema 原样发布，字段的 `description`、`default`、`examples`、`enum`、`required`、嵌套对象及数组元素说明都能随协议传给客户端。

业务 Provider 应把以下内容写入 Schema：

- 工具做什么、何时使用、是否产生修改，以及后续需要调用的工具。
- 每个输入字段的含义、单位、范围、是否必填、默认行为和必要示例。
- 每个输出字段的含义、类型、单位；空值/省略字段代表什么。
- 状态枚举和关联字段的用途，例如返回的 `id` 应传给哪个工具。

`default` 和 `examples` 是说明元数据，不会自动为业务回调填充参数；回调需实现描述的默认行为。注解工具会从 DTO 的 Java 类型与 `@Schema` 自动生成定义；显式 Provider 则直接使用业务提供的 Schema。原来的无输出 Schema 构造器继续支持，但不能提供完整的返回字段发现能力；新业务工具优先使用 `@McpTool`，特殊场景再使用完整构造器。

内置 8 个系统工具已经提供完整输入/输出 Schema，包含逐字段中文说明和输入默认值。输出根对象的 `data` 为业务结果，`warning` 为可选完成警告。`task_output.hasMore=false` 只表示当前文件没有更多字节，不表示任务完成；`process_stop.identity` 必须原样使用 `process_list.items[].stopIdentity`，不能使用毫秒精度的 `startedAt` 替代。这些规则直接包含在工具字段说明中。

## 身份与长期凭证

默认 `McpIdentityProvider` 调用 `UserStore.load(accountId)`。账号禁用、删除时，用户加载实现应返回 `null`；每次请求都会重新加载用户并执行权限策略。业务已有用户缓存时，缓存更新/失效仍由业务保证。

如果登录 ID 会随密码变化，需要提供稳定账号到当前身份的映射：

```java
@Bean
McpIdentityProvider mcpIdentityProvider(AccountService accounts) {
    return new McpIdentityProvider() {
        @Override public String accountId(IUserDetails user) {
            return accounts.stableAccountId(user.getUserId());
        }
        @Override public IUserDetails load(String accountId) {
            return accounts.loadEnabledUser(accountId); // 禁用或不存在返回 null
        }
    };
}
```

凭证随机生成，明文只在创建响应返回一次，数据库保存 SHA-256 摘要。`expiresAt` 省略、为 `null` 或 `0` 时永久有效；正数是到期毫秒时间戳，到期拒绝新请求。重启、网页退出及网页会话到期不会改变凭证自身的到期时间，也不会自动续期；撤销、暂停 MCP 或账号失去权限在后续请求生效。凭证不用于网页登录和 `/api/mcp` 管理认证。暂停或到期不会终止已经启动的后台进程，需要明确取消任务。详见[有效期规则和创建示例](configuration.md#凭证有效期)。

## 存储、审计和执行器扩展

| Bean | 业务可替换的行为 |
| --- | --- |
| `McpStore` | 凭证、运行开关、任务、审计的持久化 |
| `McpIdentityProvider` | 稳定账号 ID 和当前用户加载 |
| `McpAccessPolicy` | 使用、管理和工具调用权限；必须提供 |
| `McpToolProvider` | 注册任意业务工具；可有多个 Bean |
| `McpAuditTargetProvider` | 从参数中提取允许展示的目标 ID；默认空对象 |
| `McpTaskOutputResolver` | 本实例任务输出文件路径，包括兼容旧路径 |
| `CommandExecutor` | 本机命令启动策略；返回受任务服务跟踪的 `Process` |
| `McpManagementGuard` | 网页管理写请求的来源及请求头校验 |

`McpStore.create` 必须原子地创建唯一 key，重复必须报错，不能用 `save` 覆盖模拟；任务先成功持久化，再派发进程。跨实例共享凭证时，`get` 必须能看到已提交的撤销，不能读到未失效的本地缓存或延迟副本。默认 ORM 存储关闭查询缓存、从主节点读取，并通过数据库唯一索引保证原子创建。

审计默认不复制命令、密码、Token、文件内容或工具输出。可以只提取非敏感目标字段：

```java
@Bean
McpAuditTargetProvider auditTargets() {
    return (tool, args) -> tool.equals("order_query")
        ? java.util.Map.of("orderId", args.path("id").asText()) : java.util.Map.of();
}
```

每条审计目标最多 16 项，字段名最多 128 字符，值最多 512 字符。审计开始记录失败时不执行工具；操作完成后审计更新失败会返回提示，避免客户端误以为没有执行。
