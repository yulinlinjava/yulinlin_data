# 行为风控 security-risk

[接口安全](README.md) / 行为风控

独立可选模块，统计用户主动进入播放时获取播放链接的行为。快切按不同资源统计，全天候按时间覆盖统计；列表、预加载、心跳和续签应使用其他行为类型。组件负责识别，业务 RiskControl 负责是否限制、限制多久和存储限制状态。异步行为历史不作为限制是否有效的权威来源。

## 依赖与最小配置

引入 com.yulinlin:security-risk:3.0。默认 disabled；默认 ORM 行为存储配合 mysql、h2 或 postgresql，业务也可以提供自己的 BehaviorStore。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security-risk</artifactId>
    <version>3.0</version>
</dependency>
```

```yaml
yulinlin:
  h2:
    schema-mode: CREATE
  security:
    risk:
      enabled: true
      mode: BLOCK          # 必须提供 RiskControl；OBSERVE 只观察新命中
      group: risk-db       # ORM 数据源组名称，省略则默认组
      behaviors:
        video-play-url:
          components: [fast-switch, active-access]
          fast-switch:
            rapid:
              window: 5m
              gap: 2s
              min-videos: 50
            normal:
              window: 15m
              gap: 5s
              min-videos: 200
              min-ratio: 0.70
          active-access:
            window: 6h
            active-ratio: 0.90
            min-requests: 60
```

以上就是两个默认组件的默认阈值；可只配置行为名及 components。mode 未配置时为 OBSERVE。每个行为可设置 enabled=false；components 可为空以停用识别。已提供 RiskControl 时，即使当前组件只观察或行为已关闭，业务返回的已有有效限制仍会拦截。

内置组件可分别设置 fast-switch.mode / active-access.mode 覆盖全局模式。自定义组件使用 component-modes，例如 component-modes.business-check: OBSERVE；覆盖项必须引用已选择的自定义组件。只要任一启用行为的组件最终模式为 BLOCK，就必须提供唯一的 RiskControl Bean，否则启动失败。组件重复、未知组件、无效窗口、比例或容量配置同样启动报错。

group 仅供默认 OrmBehaviorStore 选择数据源。自定义 BehaviorStore 可自行读取或忽略它，RiskControl 的存储不受 group 限制。框架没有 block-duration，也不代替业务默认封禁三十分钟。

## 规则语义

| 组件／档位 | 条件 |
| --- | --- |
| fast-switch / rapid | 最近 5 分钟内，切入间隔严格小于 2 秒的不同目标视频至少 50 个；不额外检查比例 |
| fast-switch / normal | 最近 15 分钟至少 200 个不同视频，其中切入间隔小于或等于 5 秒的视频比例至少 70% |
| active-access / continuous | 最近完整 6 小时中，36 个十分钟段至少 33 个有请求，并且请求总量至少 60 |

快切每次有效事件都判断，不需要等待窗口满。两个快切窗口均为 [当前时刻-window, 当前时刻]，计算一条切换时前后两个事件都必须在对应窗口内；恰好等于左边界仍计入，超出边界的前驱不能贡献快切。

先根据同一用户＋行为的原始有效事件计算相邻间隔，再按 resourceId 去重。相邻资源不同才能产生快切；同一视频在窗口内只计一份，存在符合条件的切入记录就计为快切视频。重复访问不会增加不同视频数量，但仍更新原始事件的相邻关系。第一条只建立前驱，最早第 51 条请求产生第 50 个极速目标。资源为 null 的事件不能判断快切，并中断可判定的资源前驱；全天候组件仍记录它。

普通档比例是窗口内快切不同视频数／全部不同视频数；200 是最低样本数，不是仅保留最后 200 个。一次慢切换不清零，旧事件按时间退出窗口。极速阈值恰好 2 秒不计入，普通阈值恰好 5 秒计入。

全天候只使用已完成的十分钟段，当前段记录但不参与本次判断。窗口为十分钟的整数倍，不超过 memory.retention。首次事件不在段起点时，第一次完整判断可能比六小时稍晚、不足十分钟；没有新请求时不自动触发。大量请求不能替代空闲时间：每小时访问十分钟、休息五十分钟，六小时只有 6 个活跃段。

| 示例（均为不同视频） | 默认规则结果 |
| --- | --- |
| 每秒一个 | 约五十秒达到极速档 50 个目标 |
| 每四秒一个 | 第 200 条、首条后 796 秒达到普通档 |
| 每隔一、三、五分钟，持续六小时 | 完整窗口形成后命中全天候 |
| 每小时访问十分钟，其余休息 | 不因总量大就命中全天候 |

这些是业务访问限制信号，不证明用户一定使用机器人。阈值应以正常访问和采集记录共同回放验证。

## 接入重要接口

使用 ApiRisk 时，身份来自 ISecurityManager，不读取前端提交的 userId。参数方式的顺序为登录恢复 → 风控 → 现有 ApiLimit → Controller/业务事务。JSON 方式先校验登录，待请求体完成解密和反序列化后执行风控，仍在 Controller 方法和业务事务执行前；ApiLimit 在请求体读取前执行。匿名调用受保护接口返回 401，业务返回有效限制时抛出默认 HTTP 429 的异常；具体规则和统计只供服务端查询。

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

本模块统计的是已认证的重要接口调用尝试，不能把记录当成“确实成功取得播放地址”。业务后续失败仍保留行为。播放心跳、正常续签、预加载应使用其他行为或不接入该规则；资源相同的不同请求仍计入全天候请求数，但不重复增加快切视频数。

BehaviorEvent 的第四个可选参数 eventId 只能由可信服务端提供。本机使用有界缓存，在保留期内尽量复用同一事件的结果；缓存淘汰或重启、跨实例后不保证计数层去重。数据库写入与后台重试按事件 ID 幂等。不要把任意客户端提供的固定 ID 用作事件 ID，否则会把不同请求错误合并。userId 最大 128 字符，resourceId 最大 256，eventId 最大 128；空白 ID 拒绝，resourceId/eventId 可为 null。

## 业务 RiskControl

RiskControl 是限制状态的权威来源，方法均同步调用：

```java
public interface RiskControl {
    Optional<Restriction> findRestriction(Subject subject);
    Optional<Restriction> onRisk(Subject subject, List<Match> hits);
    void release(Subject subject);
}
```

- findRestriction 只返回当前仍有效的限制，过期判断由业务完成。框架不会从审计快照恢复出一个实际有效的封禁。
- onRisk 每次命中请求最多调用一次，参数包含所有 BLOCK 组件的命中；观察组件仍执行并写入结果，但不进入处罚参数。业务自行决定限制时间、永久限制、存储位置及重复命中是否延长。
- 返回 Optional.empty() 表示业务不限制，本次继续以 SUSPECTED 放行；返回 Restriction 则拦截当前请求。查询／处置异常直接传播，不会静默当作放行。
- Restriction(id, reason, expiresAt)：id 标识同一份有效限制，expiresAt 是 epoch 毫秒；null 表示永久或不提供截止时间。到期时间供响应和审计使用，后续是否有效仍以业务查询为准。
- 建立限制后重置组件统计；已有有效限制的请求不再送入判断组件。解除或业务确认到期后重新观察。
- 方法在本机同一主体锁内调用，应快速返回，优先接入已有缓存／业务服务。框架不会替业务保证跨节点幂等；多实例同时命中、网络失败后重试必须由业务正确处理。
- 同一稳定 eventId 在本机缓存未淘汰时，onRisk 失败后重试不重复计算组件；没有 eventId、进程重启或跨节点重试，仍由业务保证处置幂等。

可直接运行 [AdminRiskControlTest](../../admin/src/test/java/com/yulinlin/admin/risk/AdminRiskControlTest.java)：示例业务自行限制十分钟，验证到期、永久限制及人工解除。里面的内存 Map 只用于测试，生产替换为业务 DAO／缓存。业务注册该接口的 Bean 即可，框架不提供默认处罚实现。

## 自定义组件

RiskDetector 的同一 Subject 调用由服务串行化，不同主体可以并发。业务以 Bean 注册后，在 components 中选择其 id；仅注册而未选择的组件不会执行。使用自己的 ID 代替某个内置 ID 即可替换判断；同 ID 重复注册启动报错。

```java
import com.yulinlin.security.risk.RiskDetector;
import com.yulinlin.security.risk.RiskModels.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class BusinessRiskDetector implements RiskDetector {
    public String id() { return "business-check"; }

    public Detection detect(BehaviorEvent event, long occurredAt) {
        boolean hit = "sensitive".equals(event.resourceId());
        var matches = hit
                ? List.of(new Match(id(), "sensitive-resource", occurredAt, occurredAt,
                        "命中业务资源规则", Map.of()))
                : List.<Match>of();
        return new Detection(id(),
                hit ? DetectionStatus.MATCHED : DetectionStatus.NORMAL,
                null, matches, Map.of());
    }

    public void reset(Subject subject) {
        // 有状态组件在这里清理指定主体；这个示例无状态。
    }
}
```

配置 components: [fast-switch, active-access, business-check] 即可启用。自定义参数由业务配置类管理；默认服务不会自动发现或序列化自定义组件内部对象。需要 Redis、分布式统计或重启恢复时，组件自己管理，且应考虑同步调用耗时。

组件返回 Detection(componentId, status, reason, matches, statistics)。状态为 OBSERVING、NORMAL、MATCHED、CAPACITY；只有 MATCHED 能携带且必须携带 Match。Match 包含组件 ID、档位 ID、窗口、原因、计数指标。快切指标 videos/fastVideos，全天候指标 requests/activeBuckets/totalBuckets；比例可从计数计算。所有启用组件都会收到事件，不因第一个命中而短路。

reset 必须幂等，在建立／发现新的限制、解除或豁免时调用。本机空闲回收及服务关闭调用可选 evict(subject)，默认委托 reset；使用共享 Redis 状态的组件可覆盖 evict，只释放本机资源并自行管理共享状态过期，避免一个节点退出就清掉其他节点的数据。自定义组件异常直接传播，不当作 NORMAL。内置两个组件由服务创建，业务传入的是额外组件列表。

## 自定义风控拦截提示

@ApiRisk 在业务返回限制时抛出 ApiRiskException，不自行写响应；JSON、路径和参数入口一致。业务可以提供 @ExceptionHandler 决定状态码、文案及结构。手工入口仍返回 Decision，调用方检查 allowed 后自行抛出异常。

```java
@ExceptionHandler(ApiRiskException.class)
public ResponseEntity<Map<String, String>> riskBlocked(ApiRiskException error) {
    return ResponseEntity.status(429).headers(error.getHeaders())
            .body(Map.of("code", "VIDEO_RISK", "message", "访问暂时受限，请稍后再试"));
}
```

ApiRiskException 默认 HTTP 429。只有业务提供正的截止时间时才生成 Retry-After，向上取整且至少一秒；永久／未知截止时间不发送该头，getRetryAfterSeconds() 返回 0。Decision.blockedUntil=0 不代表放行，必须看 allowed/status。公开播放接口的错误响应只返回业务提示；管理页面可通过下述有权限校验的报告接口查询规则与统计。启用全局响应加密时，可按已有 CryptoAdvice 规则决定错误是否加密。

## 导入历史行为并回放

RiskReplay 接受 ReplayEvent(userId, behaviorType, resourceId, eventId, occurredAt)。历史时间必须为原始 epoch 毫秒；不要循环调用在线 checkAndRecord 导入旧记录，它使用当前服务器时间。

```java
var replay = new RiskReplay(riskProperties);
var results = replay.replay(events);

results.forEach(row -> System.out.printf("原始下标=%d 时间=%d 组件=%s 命中=%s%n",
        row.inputIndex(), row.event().occurredAt(),
        row.decision().components(), row.decision().matches()));

// 每次 replay 都调用工厂，不能传线上正在使用的组件实例。
var customReplay = new RiskReplay(riskProperties,
        () -> List.of(new BusinessRiskDetector()));
```

每次回放使用空白独立状态，稳定按时间升序排序，同一毫秒保留输入顺序，不修改输入 List。inputIndex 是排序前从零开始的下标。事件时间驱动窗口和去重缓存到期，不需要 sleep，不自动补造结尾请求。

回放只验证识别：不初始化数据库、不创建后台工作线程、不恢复线上快照、不调用 RiskControl。即使配置 BLOCK，也只返回允许访问的 SUSPECTED 及完整命中结果，不伪造业务封禁时长、到期或解除。在线与回放复用相同组件，但业务限制后暂停统计的效果不在离线回放里模拟。

自定义工厂必须返回新的隔离实例；框架不能阻止用户组件自行访问线上 Redis，因此应使用独立状态空间或测试实现。重复调用 replay 会重置观察窗口，不能把一段连续历史分批 replay 后拼成同一次统计。一次回放持有输入、排序副本和逐条结果，适合受控规模的样本。

Date / DateTime 用 getTime()；LocalDateTime 或无时区字符串按来源时区转换为 epoch 毫秒。不要依赖运行机器默认时区。可直接运行 [AdminRiskReplayTest](../../admin/src/test/java/com/yulinlin/admin/risk/AdminRiskReplayTest.java)，包含极速、普通、六小时、间歇访问、自定义组件及真实 JSON 导入，格式见 [admin 测试说明](../../admin/src/test/README.md#行为风控历史回放)。

直接连接历史库验证时，创建并填写 `admin/src/test/resources/application-risk-local.yml`，运行 [AdminRiskDatabaseReplayTest](../../admin/src/test/java/com/yulinlin/admin/risk/AdminRiskDatabaseReplayTest.java)。[本地数据源回放说明](../../admin/src/test/README.md#从本地配置的数据源读取历史)提供可复制的 YAML 示例，包含 MySQL／PG／H2 连接、字段映射、规则阈值及完整操作步骤；实际本地文件忽略 Git。默认读取配置时区下今天零点至运行时间的全部用户，也可指定用户及历史时间范围。ORM 在同一只读快照中分页读取全部记录，逐用户独立回放，输出 Markdown／JSON 用户报告及首次命中依据，不建表、不写库。大量数据的内存边界也见上述说明。

## 返回给管理页面的分析报告

`RiskReplay.report` 返回可直接由 Jackson 序列化的 `RiskReport`，用于命中原因、统计卡片、小时分组和趋势图。它复用同一组检测组件，不改变阈值，也不调用 RiskControl；`suspected=true` 表示这段历史命中过规则，当前是否封禁仍由业务 RiskControl 决定。

```java
var subject = new RiskModels.Subject(userId, "video-play-url");
RiskReport report = new RiskReplay(riskProperties).report(
        subject, from, to, ZoneId.of("Asia/Shanghai"), events);
// Controller 直接 return report，或者放入业务已有的响应包装。
```

`from / to` 为 epoch 毫秒，范围 `[from,to)`。调用方必须提供这个用户、这个行为、这个范围的完整历史，不能只传页面当前一页；混入其他主体或范围外事件会报错。事件按时间稳定排序，同一时刻按输入顺序处理，建议查询按 `crt_time,id` 排序。相同非空 eventId 在本次报告内只保留排序后的首条，并统计重复及内容冲突；无 ID 的事件不去重。每次调用从空白状态观察，不自动补出结尾请求。

业务 HTTP 接口示例（路径、权限码和历史查询实现由业务定义，框架不会自动注册此 URL）：

```java
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.risk.*;
import com.yulinlin.security.risk.RiskModels.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.time.ZoneId;
import java.util.List;

@Configuration
@EnableConfigurationProperties(RiskProperties.class)
class RiskReportConfiguration {
    @Bean
    RiskReplay riskReplay(RiskProperties properties) {
        return new RiskReplay(properties);
        // 自定义组件沿用上节的 Supplier，每次提供隔离实例。
    }
}

// 业务实现并注册 Bean：用 ORM 查询完整历史并转换为 ReplayEvent。
interface RiskHistorySource {
    List<ReplayEvent> loadAll(Subject subject, long from, long to);
}

@RestController
class RiskReportController {
    private final RiskReplay replay;
    private final RiskHistorySource history;

    RiskReportController(RiskReplay replay, RiskHistorySource history) {
        this.replay = replay;
        this.history = history;
    }

    @ApiPerm("risk:read")
    @GetMapping("/admin/risk/users/{userId}/report")
    public RiskReport report(@PathVariable("userId") String userId,
                             @RequestParam("from") long from,
                             @RequestParam("to") long to) {
        if (from < 0 || to <= from || to - from > 86_400_000L)
            throw new IllegalArgumentException("请选择不超过一天的有效范围");
        var subject = new Subject(userId, "video-play-url");
        return replay.report(subject, from, to, ZoneId.of("Asia/Shanghai"),
                history.loadAll(subject, from, to));
    }
}
```

这里的一天是管理查询的样例上限，业务可调整，并应限定单次记录量及租户可访问范围。只做离线分析时无需启用线上 risk.enabled；仍需配置 behaviors。报告会持有历史与逐条检测结果，较大范围可由业务离线生成并缓存。框架不查询视频标题，页面记录列表、标题和翻页仍使用业务原有接口。

| 页面内容 | 返回字段 | 含义 |
| --- | --- | --- |
| 状态标签、命中原因 | `status / suspected / incomplete / hits` | NO_DATA 无记录；DISABLED 行为未开启；OBSERVING 观察不足；NOT_MATCHED 未命中；SUSPECTED 曾命中；INCOMPLETE 存在容量缺口。曾命中也可能同时 incomplete，未命中不等于确定正常 |
| 请求数、不同视频数、起止时间 | `summary` | 去重后的 requestCount、distinctResources、firstEventAt / lastEventAt；重复 ID 和缺失视频 ID 单独计数 |
| 两档快切次数及占比 | `switches.rapid / switches.normal` | count 是原始相邻不同视频的快切次数；ratio 的分母为 differentResourceSwitches；longestRun 是最长连续快切次数 |
| 最长连续访问 | `activity.longestVisit` | start / end / durationMillis / requestCount；相邻请求间隔严格超过五分钟才断开，是请求序列跨度，不代表观看时长 |
| 活跃分钟与峰值 | `activity` | activeMinutes、longestConsecutiveMinutes、peakMinuteRequests、peakFiveMinuteRequests；五分钟峰值按连续五个自然分钟求和 |
| 首次命中证据 | `hits[].fastSwitch / activeAccess / statistics` | 保存首次触发时的窗口；同一毫秒后续请求不计入首次证据。firstMatchedAt / lastMatchedAt 表示该规则首次／末次命中；matchedRequestCount 是命中请求数，不是独立攻击次数 |
| 六小时覆盖图 | `bestActiveWindow` | 检测时已经形成的完整窗口中，优先覆盖桶最多、再请求最多的窗口；没有完整窗口时为 null |
| 分钟趋势与小时分组 | `minutes / hours` | `{start,end,count}`，包含起点、不包含终点；分钟／小时只返回非空桶，页面可补零；小时按 zoneId 分桶 |
| 实际阈值说明 | `rules` | 本次配置快照。极速 minRatio 为 null，表示没有占比要求；禁用的内置组件规则为 null，自定义组件 ID 单独列出 |

所有时间／时长都是毫秒，比例是 `0..1`，分母为零时为 null；页面展示百分比时乘以 100。不传 ZoneId 的重载使用 UTC，建议明确传入业务时区。

注意两种占比不同：`switches.normal.ratio` 是**所选范围的快切次数 / 不同视频切换次数**；`hits[].fastSwitch.fastVideoRatio` 是**首次命中窗口内的不同快切目标视频数 / 不同视频数**，后者才是普通档 70% 判定的依据。极速仍为五分钟内 `<2秒` 的不同快切目标视频至少 50 个，没有占比条件；普通档包含恰好五秒。

全天候按完整六小时里的十分钟桶覆盖计算，不表示六小时内每分钟都在访问。`activeAccess` 同时返回 activeBuckets / totalBuckets、实际 activeMinutes、含窗口边界的最长无请求间隔、包含零计数的 buckets，供人工复核。`complete` 仅说明检测器的观察时间足够，不能证明来源数据库没有漏记。`hits[].endInclusive` 在快切为 true、全天候为 false；自定义组件为 null，保留其 reason / statistics 而不猜测窗口语义。

现有本地名单可运行 [AdminRiskReportTest](../../admin/src/test/java/com/yulinlin/admin/risk/AdminRiskReportTest.java) 导出单用户 JSON、多人 JSON 与详细依据，步骤见 [admin 测试说明](../../admin/src/test/README.md#导出给页面使用的详细报告)。

## 存储与性能边界

默认 BehaviorStore 使用现有 ORM 的普通查询、批量插入、按主键更新及事务，没有手写复杂 SQL、数据库 JSON 查询或方言分支。自定义 Bean 自动替换默认实现。

- 只有 yulinlin_risk_event 和 yulinlin_risk_state 两张当前使用的表。复用数据源 schema-mode（CREATE / VALIDATE / NONE），无需额外 schema-packages；初始化和可读性检查失败阻止启动。详见[自动建表与索引](../datasources/schema.md)。
- 时间为 epoch 毫秒 BIGINT，payload 为完整 JSON 文本。事件按 ID 幂等，快照按进程 streamId 与版本单调更新；快照保存默认组件的完整有界状态，旧版本不能覆盖新版本。
- 启动阶段完成默认组件快照恢复后才接受检查，避免恢复与在线事件合并造成重复统计。恢复失败记录错误，未恢复主体从新窗口观察。配置签名变化时旧统计失效；多实例只恢复主体最新一份，不累加继承快照。
- 快照中的 restriction 只用于审计；当前是否限制始终查询业务 RiskControl。快照和业务限制不是同一事务。
- 请求线程不访问 BehaviorStore，但会同步访问业务 RiskControl 和自定义组件；默认识别在内存执行。
- 明细队列满时丢弃新增明细并增加 droppedEvents，内存统计继续；写入失败有限重试。快照失败保留脏状态，稍后保存最新完整版本。
- 内存默认最多 10000 个主体，单主体每个快切窗口最多 20000 条资源事件；容量满时返回 CAPACITY 并说明统计不完整。某档容量不完整时不据其比例判定命中，另一完整档仍可独立命中。新增主体容量不足也不会绕过 RiskControl 已有有效限制。
- 空闲超过 memory.retention 且快照已保存的主体可回收，之后重新观察。资源事件窗口会自行淘汰旧记录，全天候只保留必要十分钟桶。
- 默认单机统计，多实例不保证即时一致。进程崩溃可能丢失尚未落库的明细和统计；正常关闭等待 shutdown-timeout，超时不承诺全部保存。

可选调优配置，通常不必填写：

```yaml
yulinlin:
  security:
    risk:
      memory:
        maximum-size: 10000
        maximum-events-per-subject: 20000
        retention: 24h                 # 最多 7d，必须覆盖组件窗口
        restore-on-startup: true
      storage:
        retention: 24h                 # >= memory.retention；这里没有 group
        cleanup-interval: 1h
      async:
        batch-size: 500
        queue-capacity: 50000
        flush-interval: 1s
        snapshot-interval: 5s
        retry-count: 3
        retry-interval: 5s
        shutdown-timeout: 10s
```

## 管理接口与扩展

RiskService.inspect / listRisks 返回本机最后观察的统计与限制审计信息，不代表业务当前权威限制列表；业务当前状态应查询 RiskControl。history 是同步数据库管理查询，分页 limit 为 1..1000，时间范围 [from,to)，按时间、事件 ID 排序。业务自行给管理入口加权限。

release 调用业务 RiskControl.release，再重置组件。exempt 保留本机临时豁免能力，但不会覆盖业务已有有效限制；若需解除，先由业务解除限制。管理变化不自动广播其他实例。metrics 提供主体数、待写队列、丢弃、存储失败、容量绕过及恢复状态。

完整替换 RiskService Bean 时，业务自行管理生命周期、识别和处置流程。框架不自动踢登录会话，也不实现 JWT 撤销。

## 从旧版迁移

- storage.group 改为顶层 risk.group。
- 删除 block-duration；BLOCK 必须提供业务 RiskControl。旧的框架临时限制不自动迁移为业务处罚记录。
- behaviors.*.rules、ACTIVE_MINUTES / DENSE_ACCESS 改为 components 和上述两类组件配置。
- RiskRule、RiskActionHandler 已移除，分别迁移至 RiskDetector、RiskControl。旧配置会明确绑定失败，避免静默失效。
- Match 改为组件／档位／统计指标；Decision 增加 components 与 restriction。Snapshot / Restored / View 改为完整组件状态，不再暴露旧 Minute 列表；自定义 BehaviorStore 需同步调整。
- 旧分钟快照不兼容，首次升级重新观察；旧 yulinlin_risk_minute 表不再使用，框架不会擅自删除。旧行为明细仍可读取，其旧计数字段不会映射为新版组件统计。
- RiskReplay 的自定义扩展改为 Supplier<List<RiskDetector>>，每次回放创建新实例。回放不再模拟固定封禁时长。

## 验证

覆盖严格两秒、五秒包含边界、200 个视频与比例边界、50 个不同快切目标、重复视频、缺失资源、窗口外前驱、六小时覆盖率与请求数、首次不完整时间段、状态容量、快照恢复、业务期限／永久限制／拒绝处罚、重复处置失败重试、组件模式、自定义组件、配置绑定、MVC JSON／解密／异常响应、ORM 实际存储及回放隔离。

```text
mvn -pl security-risk,admin -am test -Dtest=Risk*Test,ApiRiskExceptionTest,OrmBehaviorStoreTest,AdminRisk*Test -Dsurefire.failIfNoSpecifiedTests=false
```

H2 覆盖普通模式和 MYSQL 兼容模式，不等于真实 MySQL 服务验证。RiskPostgresqlIntegrationTest 需要显式提供 pg.test.url，使用随机 Schema 并在结束后清理。性能需按实际主体数、事件量和快照吞吐压测。
