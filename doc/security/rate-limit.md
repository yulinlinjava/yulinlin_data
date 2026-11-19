# 限流与 IP 黑名单

[文档首页](../README.md) / [接口安全](README.md)

注解限流、业务黑名单和 Nginx 可信真实 IP。

## 本页导航

- [接口限流](#接口限流)
- [IP 黑名单](#ip-黑名单)
- [自定义 IP 获取](#自定义-ip-获取)
- [Nginx 真实 IP](#nginx-真实-ip)

## 接口限流

`@ApiLimit` 可以放在 Controller 类或方法上，方法配置覆盖类配置。默认同一客户端对同一接口每分钟最多调用 60 次，并按 IP 隔离：

```java
import com.yulinlin.security.annotation.ApiLimit;
import com.yulinlin.security.limit.LimitBy;

@RestController
public class VideoController {

    // 等价于 count=60、interval="1m"、by=LimitBy.IP
    @ApiLimit
    @PostMapping("/video/list")
    public R<?> list(@RequestBody VideoListPo request) {
        return R.ok(videoService.list(request));
    }

    // 设备 ID 缺失或非法时自动回退到真实 IP
    @ApiLimit(count = 20, interval = "10s", by = LimitBy.DEVICE)
    @PostMapping("/video/like")
    public R<?> like(@RequestBody VideoLikePo request) {
        return R.ok(videoService.like(request));
    }
}
```

只写 `@ApiLimit(count = 20)` 等价于 `count=20, interval="1m", by=LimitBy.IP`：同一 IP 对同一接口在本机一个 60 秒固定窗口内最多放行 20 次，第 21 次拒绝。窗口从该客户端的首次请求开始计时，满 interval 后下一次请求开启新窗口。

interval 支持 `500ms`、`10s`、`1m`、`1h` 等带单位的时长。没有 `@ApiLimit` 的接口不参与限流；`OPTIONS` 请求跳过限流计数，但仍先检查 IP 黑名单。

超限时拦截器抛出 `com.yulinlin.security.limit.ApiLimitException`，不写响应体、不提交响应，Controller 不会执行。异常继承 ResponseStatusException，默认 HTTP 429，携带 Retry-After 响应头。业务方可通过 @ExceptionHandler 自己决定提示文案、返回结构和状态码。异常提供 getCount()、getInterval()、getBy() 和 getRetryAfterSeconds()；重试时间为距离本窗口结束的秒数，向上取整且至少为 1。

引入 starter 且没有自定义处理器时，StarterExceptionHandler 保留 Retry-After 并返回默认 JSON：

```json
{
  "data": null,
  "code": 429,
  "crypt": false,
  "ok": false,
  "msg": "请求过于频繁，请稍后再试",
  "timestamp": 1792070400000
}
```

业务自定义示例（优先于 starter 的默认处理器）：

```java
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.security.limit.ApiLimitException;
import com.yulinlin.security.limit.IpBlockedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BusinessExceptionHandler {
    @ApiCrypto(false) // 使用全局加密时，明确让这份错误提示保持明文
    @ExceptionHandler(ApiLimitException.class)
    public ResponseEntity<Map<String, Object>> limited(ApiLimitException error) {
        return ResponseEntity.status(429)
                .headers(error.getHeaders())
                .body(Map.of(
                        "code", "VIDEO_REQUEST_TOO_FAST",
                        "message", "操作太频繁，请稍后再试",
                        "retryAfterSeconds", error.getRetryAfterSeconds()));
    }

    @ApiCrypto(false)
    @ExceptionHandler(IpBlockedException.class)
    public ResponseEntity<Map<String, Object>> blocked(IpBlockedException error) {
        // error.getIp() 可用于业务审计，不必返回给客户端。
        return ResponseEntity.status(403)
                .body(Map.of("code", "IP_BLOCKED", "message", "访问受限，请联系客服"));
    }
}
```

自定义处理器通过 headers(error.getHeaders()) 保留重试提示，也可按业务协议自行设置；不依赖 starter 时，同样可以使用上述处理器。不提供自定义处理器时由 Spring MVC 默认处理 HTTP 429，响应体采用应用自己的默认错误配置。

各接口按 IP 或设备 ID 独立计数，不活跃记录随 interval 到期淘汰。maximum-size 限制本机保留的计数器数量；达到容量上限而被提前淘汰的客户端再次访问时会重新计数。多实例各自计数，不提供全局统一额度。

### IP 黑名单

IP 黑名单由业务实现 `IpBlacklist` 接口，自行选择数据存储。接口接收 `IpResolver` 解析的客户端地址，是否验证可信代理由选用的解析器决定：

```java
import com.yulinlin.security.limit.IpBlacklist;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfiguration {

    @Bean
    public IpBlacklist ipBlacklist(IpBlacklistService service) {
        return service::isBlocked;
    }
}
```

一个简单的本地实现：

```java
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpBlacklistService {
    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();

    public boolean isBlocked(String ip) {
        return blockedIps.contains(ip);
    }

    public void block(String ip) {
        blockedIps.add(ip);
    }

    public void unblock(String ip) {
        blockedIps.remove(ip);
    }
}
```

没有业务 Bean 时，框架注册默认空实现并放行所有 IP。命中黑名单时拦截器抛出 `com.yulinlin.security.limit.IpBlockedException`，不写响应体、不设置状态码、不提交响应，也不进入 Controller 或消耗限流额度。异常继承 `ResponseStatusException`，默认 HTTP `403`，通过 `getIp()` 提供本次被拒绝的地址。黑名单检查对所有 Controller 生效，不要求存在 `@ApiLimit`，并且先于 `OPTIONS` 放行判断。

业务方使用上面的 `@ExceptionHandler(IpBlockedException.class)` 自定义状态码、返回结构及提示文案；是否明文响应也由业务处理器及加密配置决定。未自定义时由已有的 starter 异常处理器或 Spring MVC 默认异常处理机制处理 `403`，拦截器不再强制返回固定 JSON。原来依赖固定错误结构的业务应补充异常处理器。

`isBlocked` 会在每次业务请求进入 Controller 前执行，应使用快速查询，避免同步慢 SQL。可使用业务缓存，或将数据库黑名单定时加载到本地。拉黑、解除、过期时间、CIDR 规则和管理接口都属于业务实现；框架传入 `IpResolver` 解析的地址。实现抛出的异常不会被静默当作放行，避免安全检查在故障时无声失效。

### 自定义 IP 获取

`com.yulinlin.security.limit.IpResolver` 是可替换的函数接口，方法为 `String resolve(HttpServletRequest request)`。安全模块默认注册 `HeaderIpResolver`；业务方声明同类型 Bean 后默认实现自动退让。`@ApiLimit` 和 `IpBlacklist` 共用该接口，同一请求内复用解析结果；设备标识缺失或无效时也使用此结果。

默认按以下顺序读取，跳过空白及不区分大小写的 `unknown`，结果去掉首尾空白：

1. `X-Forwarded-For` 的第一项；第一项无效则继续下一个请求头，不搜索后续项。
2. `Proxy-Client-IP`、`WL-Proxy-Client-IP`。
3. `HTTP_X_FORWARDED_FOR`、`HTTP_X_FORWARDED`、`HTTP_X_CLUSTER_CLIENT_IP`、`HTTP_CLIENT_IP`。
4. `HTTP_FORWARDED_FOR`、`HTTP_FORWARDED`、`HTTP_VIA`、`REMOTE_ADDR`。
5. `request.getRemoteAddr()`。

该顺序与 `RequestUtil.getIp()` 的旧逻辑一致，不额外读取 `X-Real-IP`；除第一项外不拆分列表，也不验证字符串是否为 IP。默认实现不判断可信代理，网关必须清理或覆盖客户端提供的这些头，并限制后端直连访问。`HTTP_VIA` 等头不保证是 IP，业务方可通过自定义实现收紧规则。无请求时默认实现返回 `null`；限流中自定义实现返回空值会回退连接地址，抛出的异常直接向上传递。

例如直接按连接地址限流：

```java
import com.yulinlin.security.limit.IpResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClientIpConfiguration {
    @Bean
    public IpResolver ipResolver() {
        return request -> request == null ? null : request.getRemoteAddr();
    }
}
```

业务代码也可注入该 Bean 调用 `resolve(request)`。实现需要线程安全，避免每次调用执行慢 SQL。关闭 `limit.enabled` 只关闭限流及黑名单接入，仍可注入 IP 解析器。`starter` 中静态的 `RequestUtil.getIp()` 不会自动调用此 Bean；需要自定义策略的代码应注入 `IpResolver`。

需要保留此前的可信代理策略时，可将上例 Bean 方法改为：

```java
@Bean
public IpResolver ipResolver(com.yulinlin.security.SecurityLimitProperties properties) {
    return new com.yulinlin.security.limit.TrustedProxyIpResolver(properties);
}
```

只有显式使用 `TrustedProxyIpResolver` 时，`limit.ip-header` 和 `limit.trusted-proxies` 才生效；默认 `HeaderIpResolver` 不读取这两项。这是默认行为的变更，已有部署如需保持原策略应注册上述 Bean。

### Nginx 真实 IP

后端由本机 Nginx 代理时推荐：

```nginx
location /api/ {
    if ($request_method = OPTIONS) {
        return 204;
    }

    rewrite ^/api/(.*)$ /$1 break;
    proxy_pass http://127.0.0.1:9091;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

默认实现读取 `X-Forwarded-For` 第一项，因此这里使用 `$remote_addr` 覆盖该头，不使用追加客户端原始头的 `$proxy_add_x_forwarded_for`。Java 服务端口应只允许可信 Nginx 访问。

如果 Nginx 前面还有 CDN 或负载均衡，必须先正确配置 Nginx `real_ip` 模块的可信来源，使 `$remote_addr` 恢复为真实客户端 IP，再转发给 Java。选择 `TrustedProxyIpResolver` 时，只有连接来源匹配 `trusted-proxies` 才读取配置的单值头（默认 `X-Real-IP`）；缺失、非法或包含多个值时回退连接地址。可信来源支持单个 IPv4、IPv6 和 CIDR。

`LimitBy.DEVICE` 读取 `Device-Id`，接受最长 128 个字符的字母、数字、点、下划线、冒号和短横线。设备 ID 由客户端提供，能够被修改或轮换，因此视频列表、详情和防采集接口应优先按 IP；设备维度适合点赞、收藏等正常设备行为的额度隔离，不能替代登录鉴权。

限流配置默认值：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否处理 `@ApiLimit` |
| `maximum-size` | `50000` | 本机同时保留的计数器上限，不是单接口请求次数 |
| `ip-header` | `X-Real-IP` | 仅显式使用 `TrustedProxyIpResolver` 时生效 |
| `device-id-header` | `Device-Id` | `LimitBy.DEVICE` 使用的请求头 |
| `trusted-proxies` | `127.0.0.1`、`::1` | 仅 `TrustedProxyIpResolver` 使用的代理地址或 CIDR |

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
