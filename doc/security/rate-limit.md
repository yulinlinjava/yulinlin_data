# 限流与 IP 黑名单

[文档首页](../README.md) / [接口安全](README.md)

注解限流、业务黑名单和 Nginx 可信真实 IP。

## 本页导航

- [接口限流](#接口限流)
- [IP 黑名单](#ip-黑名单)
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

支持 `500ms`、`10s`、`1m`、`1h` 等 Spring Duration 写法。没有 `@ApiLimit` 的接口不参与限流；`OPTIONS` 请求直接放行。超出限制时返回 HTTP `429`、`Retry-After` 响应头和未加密的统一 JSON：

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

每条计数记录的 Key 是 `Controller 方法 + 限流维度 + IP/设备 ID`。Caffeine 根据该接口的 `interval` 淘汰不活跃记录，并用 `maximum-size` 防止大量随机 IP 或设备 ID 持续占用内存；未开启统计功能。达到容量上限而被提前淘汰的客户端再次访问时会重新计数。

### IP 黑名单

IP 黑名单由业务实现 `IpBlacklist` 接口，框架不规定数据来自内存、Caffeine、Redis 还是数据库。接口只接收已经完成可信代理检查并规范化的真实客户端 IP：

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

没有业务 Bean 时，框架注册默认空实现并放行所有 IP。命中黑名单时不进入 Controller，直接返回 HTTP `403` 和未加密的统一 JSON，`msg` 为 `当前 IP 已被禁止访问`。黑名单检查对所有 Controller 生效，不要求存在 `@ApiLimit`。

`isBlocked` 会在每次业务请求进入 Controller 前执行，不要在里面直接进行慢 SQL 查询。推荐使用内存集合、Caffeine、Redis Set，或者把数据库数据加载到本地后定时刷新。拉黑、解除、过期时间、CIDR 规则和管理接口都属于业务实现；框架只负责可靠传入真实 IP。实现抛出的异常不会被静默当作放行，避免安全检查在故障时无声失效。

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
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

框架先检查 Java 服务看到的 TCP 来源地址。只有来源匹配 `trusted-proxies` 时才读取 `X-Real-IP`；否则忽略所有转发 IP，使用连接来源地址。缺失、非法或包含多个值的 `X-Real-IP` 也会回退连接地址，避免客户端伪造。`trusted-proxies` 支持单个 IPv4、IPv6 和 CIDR，例如容器网络可以明确增加 `172.16.0.0/12`，不要无条件信任所有地址。Java 服务端口也应只允许可信 Nginx 访问。

如果 Nginx 前面还有 CDN 或负载均衡，必须先正确配置 Nginx `real_ip` 模块，使 `$remote_addr` 恢复为真实客户端 IP，再转发给 Java。框架不会取 `X-Forwarded-For` 的第一个值。

`LimitBy.DEVICE` 读取 `Device-Id`，接受最长 128 个字符的字母、数字、点、下划线、冒号和短横线。设备 ID 由客户端提供，能够被修改或轮换，因此视频列表、详情和防采集接口应优先按 IP；设备维度适合点赞、收藏等正常设备行为的额度隔离，不能替代登录鉴权。

限流配置默认值：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否处理 `@ApiLimit` |
| `maximum-size` | `50000` | 本机同时保留的计数器上限，不是单接口请求次数 |
| `ip-header` | `X-Real-IP` | 仅从可信代理连接读取的客户端 IP 请求头 |
| `device-id-header` | `Device-Id` | `LimitBy.DEVICE` 使用的请求头 |
| `trusted-proxies` | `127.0.0.1`、`::1` | 可以提供真实 IP 请求头的代理地址或 CIDR |

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
