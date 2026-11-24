# MCP 业务工具

[文档首页](../README.md) / [MCP](README.md)

外部 AI 只能看到 `tools/list` 返回的工具定义：名称、说明、`inputSchema`、`outputSchema`。它读不到项目文档、Java 注释或 Swagger 页面，所以**工具和字段的说明必须写进注解或 Schema 里**。

推荐用 `@McpTool` 注册工具。只有动态结构或特殊序列化的场景，才需要用[显式注册](#显式注册)。

## 用注解注册

给 Spring 单例 Bean 的 public 方法加 `@McpTool`，用 Java DTO 定义参数和返回值，用 `@Schema` 写字段说明：

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
        // 工具权限不代替数据权限，这里仍要按当前用户过滤
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

要点：

- AI 传的参数就是 DTO 本身，例如 `{"id":"order-123"}`，不会再包一层 `request`。
- 返回值会自动放进 `structuredContent.data`。**不要**再返回 `R<OrderResult>`。
- 工具在令牌**所属账号**的身份下执行，`SecurityContext.getUserId()` 返回的就是这个账号。调用结束或抛异常后身份会恢复，不会创建网页会话，也不会传给子线程。
- 调用经过 Spring 代理，所以事务、AOP 拦截器照常生效。

### `@McpTool` 属性

| 属性 | 说明 |
| --- | --- |
| `name` | 工具名，1–128 位字母、数字或 `_.-`，全局唯一，重复会导致启动失败。建议用 `业务.动作` 或 `业务_动作` 风格，方便令牌按前缀授权（如 `order.*`） |
| `description` | 必填。写清楚工具做什么、什么时候用、会不会修改数据、结果里的 id 接下来传给哪个工具 |
| `readOnly` | 默认 false。为 true 时可以被令牌范围 `@readonly` 覆盖。它只是提示，不代替服务端授权 |

### 方法签名

方法最多接收两个参数：

- **一个请求 DTO**（可选）：参数 Schema 由它生成。
- **一个 `McpCallContext`**（可选）：框架会自动注入，不会出现在参数 Schema 里。

```java
@McpTool(name = "file.list", description = "列出目录下的文件", readOnly = true)
public List<FileItem> list(ListRequest request, McpCallContext context) {
    String root = context.attribute("file.root");   // 令牌上的业务属性
    if (root == null) throw new IllegalArgumentException("该令牌未授权任何目录");
    return files.list(root, request.path());
}
```

无参方法、只有 `McpCallContext` 的方法，以及返回 `void` 的方法都支持。

### `McpCallContext`

| 方法 | 说明 |
| --- | --- |
| `principal()` | 调用方身份：`type`、`credentialId`、`name`、`accountId`、`scope`、`attributes` |
| `credentialId()` | 令牌 id（或其他认证方式的凭证 id） |
| `user()` | 令牌所属账号，已经通过 `canUse` 校验 |
| `attribute(key)` / `attributes()` | 令牌上的业务属性，由管理员签发时设置，框架只透传不解释 |
| `requestId()` | 本次请求的追踪 id（与 `command_execute` 的幂等 `requestId` 无关） |
| `McpCallContext.current()` | 静态方法，在工具调用链里的任意位置获取当前上下文；不在工具调用中时返回 null |

`attributes` 适合做数据范围限制，例如租户、目录、项目，用法见[业务属性](tokens.md#业务属性attributes)。约定一个键名，在工具中读取并校验即可。缺少属性时应当拒绝，而不是放开全部数据。

## 自动生成 Schema 的规则

| 来源 | 自动处理 |
| --- | --- |
| 普通 JavaBean / record | 按 Jackson 可读写属性生成字段，支持 getter/setter 和 record |
| 嵌套、递归 DTO，`List<Item>`、`Page<Item>`，`Map<String, 明确类型>` | 保留具体泛型，生成嵌套结构或本地 `$defs` / `$ref`；也支持已绑定类型的泛型父类方法 |
| `@JsonProperty`、`@JsonIgnore` | 沿用 JSON 字段名和忽略规则；命名策略跟随应用的 ObjectMapper |
| `@Schema` | description、title、example/examples、defaultValue、allowableValues、required/requiredMode、nullable、数值范围、字符串长度、pattern、format、deprecated |
| Bean Validation | 请求 DTO 自动校验，不需要 `@Validated`；嵌套对象需要 `@Valid` |

- `@NotNull`、`@NotBlank`、`@NotEmpty` 会生成必填。`@Size`、`@Min`、`@Max`、`@DecimalMin`、`@DecimalMax` 等会转换成 Schema 约束。只使用 Default 校验组。
- 引用类型字段默认允许 null，基本类型不允许 null。
- `@Schema(defaultValue=...)` 只是说明，不会自动填值，默认行为要在代码里实现。
- 字段的业务含义必须自己写 `description`，框架不会猜。

**不支持**以下类型，遇到会启动失败，不会静默发布任意结构：

动态 `Object`、值类型不明的 Map、非 String 键的 Map、`JsonNode`、原始集合、未绑定泛型、抽象或多态 DTO、`Optional`、异步返回、`byte[]`、日期等特殊类型。

日期可以用格式明确的 String 或毫秒 Long 表示；异步操作可以返回一个业务任务 DTO。

### 发现范围

- 只扫描 Spring **单例** Bean，不扫描 prototype、request、session 作用域的 Bean。
- 不会自动暴露 Controller 或 Service 的所有方法，只注册加了 `@McpTool` 的方法。
- 使用 JDK 动态代理的 Bean，工具方法必须声明在接口上，否则启动时会报错提示。

## 写好说明

AI 完全依靠 Schema 理解工具，说明写得越清楚，调用越准确：

- **工具说明**：做什么、何时用、是否修改数据、后续要调用哪个工具。
- **输入字段**：含义、单位、范围、是否必填、省略时的默认行为、示例。
- **输出字段**：含义、类型、单位，null 或缺省代表什么。
- **枚举和关联**：每个状态值的含义；返回的 id 应该传给哪个工具。

## 显式注册

需要手写完整 Schema 时（动态结构、特殊序列化、复杂的 OpenAPI 组合），注册 `McpToolProvider` Bean。同名工具与 `@McpTool` 冲突时启动报错。

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.tool.McpToolDefinition;
import com.yulinlin.mcp.tool.McpToolProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;

@Bean
McpToolProvider orderTools(ObjectMapper json, OrderService orders) throws Exception {
    var input = json.readTree("""
        {
          "type": "object",
          "description": "订单查询参数。",
          "properties": {
            "id": {"type": "string", "description": "业务订单编号。", "minLength": 1, "maxLength": 64}
          },
          "required": ["id"],
          "additionalProperties": false
        }
        """);
    var output = json.readTree("""
        {
          "type": "object",
          "properties": {
            "data": {
              "type": "object",
              "properties": {
                "id": {"type": "string", "description": "业务订单编号。"},
                "status": {"type": "string", "enum": ["pending", "paid", "closed"]}
              },
              "required": ["id", "status"]
            },
            "warning": {"type": "string", "description": "可选完成警告；操作可能已成功，不要自动重试。"}
          },
          "required": ["data"]
        }
        """);
    return () -> List.of(new McpToolDefinition(
        "order_query", "按订单编号查询当前用户可以访问的订单", input, output, true,
        (context, args) -> orders.findVisible(context.user().getUserId(), args.get("id").asText())
    ));
}
```

- 参数按 JSON Schema Draft 2020-12 校验，支持嵌套、数组、`$defs` / 本地 `$ref`、组合条件，不加载远程 Schema。
- 是否拒绝未知字段由 `additionalProperties` 决定。
- `outputSchema` 描述的是**完整的** `{"data": ..., "warning": ...}` 对象，不只是 data 部分。输出校验发生在业务执行之后，校验失败不代表业务已回滚，客户端不应自动重试写操作。
- 也可以使用不带 outputSchema 的构造器，但 AI 就无法知道返回字段的含义。

## 调用结果

| 情况 | 返回 |
| --- | --- |
| 成功 | `result.structuredContent.data` 为返回值，`result.content` 同时给出 JSON 文本；审计保存失败时附带 `warning` |
| 参数不符合 Schema、未知工具 | JSON-RPC error `-32602` |
| 没有工具权限（scope 或 `canInvoke`） | HTTP 403，error `-32003` |
| 业务抛异常 | `result.isError: true`。`IllegalArgumentException`、`McpException`、`IOException` 的消息会返回给 AI，其他异常只返回通用提示 |
| 审计存储不可用 | HTTP 500，工具不会执行 |

给 AI 看的错误信息应该说明原因和下一步怎么做，不要包含内部堆栈或敏感数据。
