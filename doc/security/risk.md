# 行为风控 security-risk

[接口安全](README.md) / 行为风控

独立可选模块，保护获取播放链接等重要接口。按已认证用户和行为类型在内存中记录、判定；行为明细与统计异步批量写库。可以搭配 JWT、Sa-Token、Spring 或自定义认证实现，不注册第二个 ISecurityManager。

## 依赖与最小配置

引入 com.yulinlin:security-risk:3.0，并选择已有的 mysql、h2 或 postgresql 数据源模块。默认 disabled，不创建队列、线程或表。默认 OrmBehaviorStore 只调用现有 ModelSelectWrapper、ModelInsertWrapper、ModelUpdateWrapper、ModelDeleteWrapper 与 RouteSession 事务；SQL 生成、参数绑定、批量执行、日志都交给 ORM。没有数据库方言分支或手写业务 SQL。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security-risk</artifactId>
    <version>3.0</version>
</dependency>
```

```yaml
yulinlin:
  mysql:
    schema-mode: CREATE        # 复用已有数据源配置；使用 PG/H2 时改为对应模块
  security:
    risk:
      enabled: true
      mode: OBSERVE             # 默认；BLOCK 才临时拒绝
      block-duration: 30m
      memory:
        maximum-size: 10000     # 用户＋行为的统计对象上限
        retention: 24h          # 最多 7d
        restore-on-startup: true
      storage:
        group: mysql           # 省略则使用 ORM 默认组
        retention: 24h
        cleanup-interval: 1h
      async:
        batch-size: 500
        flush-interval: 1s
        queue-capacity: 50000
        overflow-policy: DROP_NEWEST
        snapshot-interval: 5s
        retry-count: 3
        retry-interval: 5s
        shutdown-timeout: 10s
      behaviors:
        video-play-url:
          enabled: true
          rules:
            - id: continuous-access
              type: ACTIVE_MINUTES
              enabled: true
              window: 120m
              min-active-minutes: 120
            - id: dense-access
              type: DENSE_ACCESS
              enabled: true
              window: 120m
              min-requests: 500
              min-active-minutes: 60
              short-gap: 10s
              min-short-gap-ratio: 0.70
```

以上数值是观察起点，不是适合所有业务的封禁标准。先回放正常用户与采集样本，再切换 BLOCK。程序启动后不自动热加载配置。

behaviorType 精确匹配配置键，仅接受小写字母、数字和短横线；首字符必须为字母，长度不超过 64。未配置的行为明确报错；enabled=false 的行为返回 DISABLED 并停止记录。规则 ID 在一个行为内唯一。窗口为整分钟、不超过内存保留期，storage.retention 不得短于 memory.retention；min-active-minutes 不超过窗口分钟数，比例为 0..1，queue-capacity 不小于 batch-size。

## 接入重要接口

使用 ApiRisk 时，身份来自 ISecurityManager，不读取前端提交的 userId。参数方式的顺序为登录恢复 → 风控 → 现有 ApiLimit → Controller/业务事务。JSON 方式先校验登录，待请求体完成解密和反序列化后执行风控，仍在 Controller 方法和业务事务执行前；ApiLimit 在请求体读取前执行。匿名调用受保护接口返回 401，BLOCK 命中返回 429；具体规则和统计只供服务端查询。

```java
import com.yulinlin.security.risk.ApiRisk;
import org.springframework.web.bind.annotation.*;

@RestController
class VideoController {
    @GetMapping("/video/{videoId}/play-url")
    @ApiRisk(value = "video-play-url", resource = "videoId")
    public String playUrl(@PathVariable String videoId) {
        // 只有风控允许才执行到这里，再调用业务 Service 生成链接。
        return "...";
    }
}
```

资源统一用 resource 指定字段名，按“路径变量 → 请求参数（query/form）→ JSON 请求体”的顺序提取。GET、表单、JSON 都使用同一个注解属性；JSON 示例：

```java
public record PlayRequest(String videoId) {}

@PostMapping("/video/play-url")
@ApiRisk(value = "video-play-url", resource = "videoId")
public String playUrl(@RequestBody PlayRequest request) {
    // 风控已通过，请求体仍可正常使用；业务事务放在这里调用的 Service。
    return videoService.playUrl(request.videoId());
}
```

请求正文：

```json
{"videoId":"video-123"}
```

- resource 支持点分隔嵌套 JSON 字段，例如 video.id 对应 {"video":{"id":"video-123"}}；路径变量和请求参数则按完整字段名查找。
- 同名值同时存在时，路径变量优先，其次请求参数，最后 JSON。已存在但为空白或不合法的值返回 400，不向后回退。一次请求只记录一次行为。
- JSON 请求声明 Content-Type 为 application/json 或 +json，使用 @RequestBody DTO、Map、JsonNode、JSON 字符串或 HttpEntity<DTO> 接收。先完成现有 CryptoRequestBodyAdvice 解密和转换，再按应用 ObjectMapper 的字段命名规则提取；Controller 收到的对象保持正常。
- 资源 ID 接受字符串或数字。所有来源均未找到/null 时，仍按登录用户＋行为统计，资源记为 null。空白、超过 256 字符、JSON 字段为对象/数组/布尔值时返回 400。
- 空的可选请求体、无 Content-Type 或非 JSON 正文同样经过风控，只是不从正文提取资源。请求体是否必填、是否支持该格式仍由 Spring MVC 决定；无效 JSON 在转换/解析时返回 400。已经记录的行为不会因后续参数校验或业务失败撤回。
- 不填写 resource 时，只按登录用户＋行为统计。客户端提交的 userId、behaviorType 不用于决定身份和规则。

静态资源和 OPTIONS 不计入；MVC 异步再派发不会重复记录。原先两个资源来源属性已统一为 resource，调用方直接替换属性名即可。

需要自定义资源提取、明确分开续签/预加载或复用已验证身份时，采用 Java 接口：

```java
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.risk.RiskService;
import com.yulinlin.security.risk.ApiRiskException;
import com.yulinlin.security.risk.RiskModels.BehaviorEvent;

// 在不带业务事务的入口中：
String userId = securityManager.requireUser().getUserId();
var event = new BehaviorEvent(userId, "video-play-url", videoId);
var decision = riskService.checkAndRecord(event);
if (!decision.allowed()) {
    throw new ApiRiskException(event, decision);
}
// 再调用带事务的业务 Service。
```

同一次行为选择注解或手工入口之一，不叠加记录。Java 入口是可信服务端 API；多租户可把租户和用户组合成稳定的 userId。调用时若已有框架事务或原生 Spring 活动事务，会明确报错，避免把检查放在已经执行的业务之后。

本模块统计的是已认证的重要接口调用尝试，不能把记录当成“确实成功取得播放地址”。业务后续失败仍保留行为。播放心跳、正常续签、预加载应使用其他行为或不接入该规则；资源相同的不同请求仍分别计数。

BehaviorEvent 的第四个可选参数 eventId 只能由可信服务端提供。本机使用有界缓存，在保留期内尽量复用同一事件的结果；缓存淘汰或重启、跨实例后不保证计数层去重。数据库写入与后台重试按事件 ID 幂等。不要把任意客户端提供的固定 ID 用作事件 ID，否则会把不同请求错误合并。userId 最大 128 字符，resourceId 最大 256，eventId 最大 128；空白 ID 拒绝，resourceId/eventId 可为 null。

## 自定义风控拦截提示

@ApiRisk 判定不允许访问时，抛出 com.yulinlin.security.risk.ApiRiskException，参数、表单和 JSON 请求使用同一种异常。检查入口不设置响应头、不提交响应体，Controller/业务方法不会执行；业务方可以使用 @ExceptionHandler 自行决定状态码、文案和返回结构。

ApiRiskException 继承 ResponseStatusException，默认 HTTP 429。getHeaders() 提供 Retry-After，按判定的 blockedUntil 与抛出时刻之差向上取整为秒，至少 1 秒；不再固定为 60 秒。getRetryAfterSeconds() 返回该值，getEvent() 返回行为信息，getDecision() 返回判定结果（含限制截止时间和命中规则）。这些详情供服务端处理，不要把整个异常或判定直接序列化给客户端。

```java
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.risk.ApiRiskException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BusinessRiskExceptionHandler {
    @ApiCrypto(false) // 启用全局加密时，让这份错误提示保持明文
    @ExceptionHandler(ApiRiskException.class)
    public ResponseEntity<Map<String, Object>> riskBlocked(ApiRiskException error) {
        return ResponseEntity.status(429)
                .headers(error.getHeaders())
                .body(Map.of(
                        "code", "VIDEO_RISK_BLOCKED",
                        "message", "访问过于频繁，请稍后再获取播放链接",
                        "retryAfterSeconds", error.getRetryAfterSeconds()));
    }
}
```

使用 starter 且没有自定义处理器时，StarterExceptionHandler 保留 HTTP 429、Retry-After 并返回默认 R 错误结构；不使用 starter 时，交给 Spring MVC 默认异常处理。OBSERVE 模式仍放行；匿名请求和参数错误分别维持 401、400。手工调用 checkAndRecord 仍返回 Decision，上面的 Java 示例可在拒绝时抛出同一种异常。

## 规则语义

| 类型 | 命中条件 |
| --- | --- |
| ACTIVE_MINUTES | 完整窗口内活跃分钟数达到 min-active-minutes |
| DENSE_ACCESS | 请求数、活跃分钟数、短间隔比例三个条件同时达到阈值 |

启用的规则之间为 OR。一个分钟有至少一次有效统计请求就属于活跃分钟。计算最近 N 个完整分钟，当前未结束分钟的请求会记录，但不参与当前窗口判定。第一次访问到完整观察窗口形成之前返回 OBSERVING；第一次请求不在分钟边界时，需要额外等待不足一分钟。每个用户、行为每个分钟边界后第一次请求重新评估一次，不定时扫描所有用户，也不会在没有请求时自动新封禁。

短间隔跨分钟计算；时间恰好等于 short-gap 也计入。窗口第一条记录与窗口外前驱的间隔不计入分母；窗口内长暂停计入分母，不清空历史。窗口基于服务器毫秒时间，时钟回拨时本机同一主体使用不递减时间。不同实例间隔分别计算，不拼接成全局准确序列。

默认规则不要求 resourceId，不自动推断视频长度、不同视频数量或播放完成情况。更复杂业务判断通过 RiskRule 扩展。

结果状态：

| 状态 | 是否允许 | 含义 |
| --- | --- | --- |
| OBSERVING | 是 | 完整窗口尚未形成 |
| ALLOWED | 是 | 当前未命中 |
| SUSPECTED | 是 | OBSERVE 模式命中 |
| BLOCKED | 否 | BLOCK 模式命中或已有本机限制未到期 |
| EXEMPT | 是 | 本机临时豁免 |
| DISABLED | 是 | 行为已关闭 |
| CAPACITY | 是 | 达到内存主体上限，新增主体暂时无法统计 |

## 存储与性能边界

- 请求入口不查询/写入数据库。每个用户＋行为有独立内存锁，保证本机并发不丢计数；规则扩展和回调也必须避免阻塞 IO。
- 明细使用有界队列，达到 batch-size 或 flush-interval 后刷库。queue-capacity 不含工作线程当前正在处理/重试的最多一批。满时丢弃新明细，增加 droppedEvents；内存统计继续更新。
- 明细首次失败后最多重试 retry-count 次，间隔 retry-interval，耗尽后丢弃并记录错误。统计只刷脏分钟和状态；失败保留脏数据，按 retry-interval 重试最新快照，不套用明细丢弃规则。
- 已限制请求保留明细，但不继续增加判断样本或自动延长限制。到期/人工解除后开始新的观察窗口，不因旧证据立即再封；历史记录保留。豁免到期同样开始新窗口。
- 建表复用选中数据源的 schema-mode（CREATE / VALIDATE / NONE），风控不再提供独立的 storage.schema-mode。开启模块后把三个内部实体交给已有 initializeSchema 接口；无需额外加入 schema-packages。MySQL、PG 默认 NONE，需要自动建表时设置对应数据源为 CREATE；H2 默认 CREATE。该配置同时约束数据源原有的业务建表行为，详见[自动建表与索引](../datasources/schema.md)。NONE 时由用户先准备表；启动会检查表是否可读。
- 表为 yulinlin_risk_event、yulinlin_risk_minute、yulinlin_risk_state。时间字段用 epoch 毫秒 BIGINT，payload 是整字段 JSON 文本，不依赖数据库 JSON 查询。
- 统计快照按独立 streamId、版本持久化。每个进程的单个后台线程顺序保存，用普通 ORM 查询判断新增/更新，批量插入或按主键更新脏分钟；同批状态与分钟通过 RouteSession.transaction 提交。版本比较在 Java 中完成，不依赖 SQL upsert。事件批量查询已有 ID 后只插入新增记录，重试不会重复追加明细。
- 默认多实例各自判断和封禁，不保证跨节点即时一致。重启只恢复每个主体最近的一份有效实例快照，不能把包含继承历史的多个快照直接求和。此恢复不是全局准确统计；本机在后台恢复完成前从新观察窗口开始，已保存的限制在恢复后生效。
- 后台恢复最多 memory.maximum-size 个主体。恢复期间已经发生的本机请求保留；本机刚执行的人工解除/豁免优先。规则定义改变时不恢复旧规则快照。
- 内存满时不淘汰仍有效的限制，新增主体返回 CAPACITY 并累计 capacityBypasses。过期且已持久化的空闲主体后台移除。需监控容量，否则持续满载会降低识别覆盖。
- 数据库运行中不可用不阻塞业务，内存继续判断；启动建表/校验失败则启动报错。进程崩溃可能丢失未刷库数据。正常停机等待最多 shutdown-timeout，超时中断工作线程，驱动是否及时响应中断取决于驱动。
- 历史清理由独立线程分批删除，单批最多 1000 行，避免占用明细写入线程。活动限制/豁免不随历史保留期提前删除。

## 管理接口与扩展

以下接口不自动暴露 HTTP 管理端点；业务必须自行加管理权限和审计：

```java
import com.yulinlin.security.risk.RiskModels.Subject;
import java.time.Duration;

var subject = new Subject(userId, "video-play-url");
riskService.inspect(subject);                   // 本机状态和分钟趋势
riskService.listRisks(0, 20);                    // 本机风险分页
riskService.history(subject, fromMillis, toMillis, 0, 100); // 数据库历史：[from,to)
riskService.release(subject);                   // 本机解除，异步保存
riskService.exempt(subject, Duration.ofMinutes(30));
riskService.metrics();                          // 队列、丢弃、失败、容量不足、恢复状态
```

管理分页 limit 为 1..1000，历史结果按时间、事件 ID 排序。history 是显式同步数据库查询，不属于实时检查路径。解除和豁免不自动广播其他实例。未配置签名相同的行为不恢复旧状态；活动限制过期后旧统计不立即重新封禁。

| 扩展 Bean | 约定 |
| --- | --- |
| BehaviorStore | 替换默认数据库存储；写入幂等，快照版本单调，恢复不重复累加，需支持清理线程和管理查询并发 |
| RiskRule | type 唯一，纯内存 evaluate；可替换同名内置类型；规则变化时更新规则 id 以使历史失效 |
| RiskActionHandler | 新命中状态通知；必须快速非阻塞；异常记录但不改变已作出的放行判断 |
| RiskService | 完整替换内存风控服务，自己管理生命周期 |

DefaultRiskService 不承担 JWT 撤销，亦不自动踢人；自定义处置可按能力调用已有 LoginSessionManager。本机临时限制生效与登录会话生命周期相互独立。

## 验证

模块测试覆盖两类规则、窗口边界、跨分钟间隔、同一用户并发、队列溢出、写入失败重试、MVC 拦截、专用风控异常及业务自定义响应、JSON DTO/嵌套字段/解密请求体、配置绑定、H2 实际建表/恢复/清理与版本防回退。H2 同时验证普通模式与 MYSQL 兼容模式，不等于真实 MySQL 服务端验证；模块不再维护 MySQL 专用 SQL。RiskPostgresqlIntegrationTest 在显式提供 pg.test.url 时使用独立随机 Schema 验证真实 PG，并清理该 Schema。

```text
mvn -pl security-risk -am test -Dtest=Risk*Test,ApiRiskExceptionTest,OrmBehaviorStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

性能参数需结合实际用户数、调用频率、数据库吞吐压测，不承诺固定 QPS。

