# HTTP 请求、上传与下载

[文档首页](../README.md) / [常用工具](README.md)

JSON、表单、文件、超时设置和 404 检查。

需要替换底层 HTTP 库或统一扩展请求行为，见[HTTP 执行器规范与替换](http-executor.md)。

## 本页导航

- [HTTP 请求](#http-请求)
- [上传与下载](#上传与下载)
- [超时与错误处理](#超时与错误处理)
- [判断是否为 404](#判断是否为-404)
- [自定义客户端与生命周期](#自定义客户端与生命周期)

## HTTP 请求

公共类型在 `com.yulinlin.data.core.http`：HttpRequestClient、HttpRequest、HttpUtil、HttpResponse、HttpFile、HttpRequestException。上层调用同步阻塞；默认由 RestClientHttpExecutor 使用 Spring RestClient + JDK HttpClient 执行，用户提供 HttpExecutor Bean 即可替换。

下面是完整 Service。复用客户端，每次调用创建新的请求对象；地址是示例，需替换为自己的服务：

```java
package demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.core.http.HttpRequestClient;
import com.yulinlin.data.core.http.HttpResponse;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;

@Service
public class RemoteApiService {
    private final HttpRequestClient client;

    public RemoteApiService(HttpRequestClient client) { this.client = client; }

    public Map<String, Object> createOrder(String token, String productId) {
        HttpResponse response = client.post("https://api.example.com/orders")
                .bearerToken(token)
                .json(Map.of("productId", productId, "quantity", 1))
                .timeout(Duration.ofSeconds(5))
                .execute();
        return response.bodyAs(new TypeReference<Map<String, Object>>() {});
    }
}
```

以下为业务方法体片段，采用静态入口：

```java
import com.yulinlin.data.core.http.HttpUtil;
import com.yulinlin.data.core.http.HttpResponse;
import java.util.Map;

HttpResponse response = HttpUtil.get("https://api.example.com/users")
        .query("page", 1).query("size", 20)
        .header("X-Request-Id", "demo")
        .execute();

String text = response.getBodyAsString();
int status = response.getStatus();

HttpUtil.post("https://api.example.com/forms")
        .form(Map.of("name", "alice", "enabled", true))
        .execute();
```

| 能力 | 入口 |
| --- | --- |
| HTTP 方法 | get/head/post/put/patch/delete；其他方法 request(HttpMethod, url) |
| 查询与头 | query(name, value)、header(name, value)、headers(HttpHeaders) |
| 认证 | basicAuth(user, password)、bearerToken(token) |
| JSON | json(Object) |
| 普通表单 | form(name, value)、form(Map<String,Object>) |
| 其他 body | body(Object, MediaType) |
| 响应 | getStatus、getStatusCode、getHeaders、getBody、getBodyAsString、bodyAs |
| 只检查状态 | 请求的 status() 返回状态码，不读取正文；4xx/5xx 也直接返回 |

query 的 null 值会忽略。同一个请求只使用一种 body 类型，不能混合 json 与 form；请求构造器是可变对象，不跨线程共享。

`query(name, value)` 接收**未编码的原始值**，例如 `a+b` 会编码成 `a%2Bb`。不要先手工 URL 编码参数。URL 本身已有的 `%2F`、`%2B`、重复参数顺序和转义大小写会保留，不重复编码或重排签名参数；原始空格和中文会编码。签名 URL 不应再随意追加参数，否则仍可能使签名失效。

## 上传与下载

```java
import com.yulinlin.data.core.http.HttpFile;
import com.yulinlin.data.core.http.HttpUtil;
import java.nio.file.Path;
import java.time.Duration;

// multipart 普通字段和文件一起上传，不使用 form() 混入。
HttpUtil.post("https://api.example.com/files")
        .multipart("category", "invoice")
        .file("file", HttpFile.of(Path.of("upload/invoice.pdf")))
        .execute();

// 大文件流式落盘。
HttpUtil.get("https://api.example.com/export")
        .timeout(Duration.ofSeconds(60))
        .downloadTo(Path.of("download/export.zip"));
```

HttpFile 支持 of(Path)、of(filename, byte[], MediaType)、of(filename, InputStream, MediaType)。输入流上传使用未知长度，不提前读取流计算大小；服务器需要支持未知长度的上传。输入流不保证可重放，不重复使用已消费的原流。业务提供的流建议使用 try-with-resources，即使请求构造失败也能关闭。

`execute()` 会把响应 body 放入内存，默认最多 16MB；大文件使用 `downloadTo()` 流式落盘，不受内存响应上限限制。

下载会创建父目录，先写同目录临时文件，读取完成且 Content-Length（若提供）符合后才替换目标文件。优先原子替换；文件系统不支持时退回普通移动，不承诺断电原子性。读取失败会清理临时文件并保留旧文件；清理失败作为 suppressed 异常保留。只接受有正文的完整 2xx 响应，拒绝 3xx、4xx/5xx、HEAD、204/205 和 206/Content-Range，避免把跳转页面或分段文件覆盖成完整文件。返回响应 body 为空，内容已写磁盘。业务需控制磁盘空间并校验目标路径，不能直接信任远端路径。

## 超时与错误处理

Spring 注入客户端默认 10 秒，配置如下：

```yaml
yulinlin:
  http:
    timeout: 10s
    max-response-size: 16MB
    max-error-size: 64KB
```

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| timeout | 10s | 默认响应超时；框架自建连接池也用它设置连接超时 |
| max-response-size | 16MB | execute() 响应正文上限，超过抛出 HttpRequestException，不返回残缺数据 |
| max-error-size | 64KB | 失败响应正文保留上限，超过截断；isResponseBodyTruncated() 可判断 |

单次 `timeout(Duration)` 覆盖**响应等待与读取超时**，至少 1ms，不重建连接池、不修改其他并发请求的超时；连接超时保留客户端创建时的设置。它不是覆盖参数序列化、文件写盘和全部业务处理的严格总截止时间。没有独立的链式 connectTimeout/readTimeout 方法；高级连接配置通过自定义底层 HttpClient 提供。

正文大小配置必须大于 0 且小于 2GB；普通响应即使没有 Content-Length 也会限制实际读取量。错误正文只保留前 N 字节，截断后可能不是完整 JSON。状态检查和截断处理会关闭正文流，不为了复用连接继续消费整个大文件，必要时由底层重建该条连接。业务自定义拦截器如果提前缓冲正文，其自身内存使用不在这个限制内。

Spring 单例初始化完成后，`HttpUtil.getClient()` 与注入的默认 HttpRequestClient 是**同一个实例**，自动使用相同的执行器、ObjectMapper 和上述配置，不必手工 setClient。初始化回调（如 `@PostConstruct`）中的业务请直接注入客户端，不依赖静态绑定完成时机。

`HttpUtil.withTimeout(duration)` 和 `client.withTimeout(duration)` 返回轻量视图，只改变默认响应超时，继续使用同一个执行器，不创建连接池，也不绕过自定义实现。关闭视图不关闭共享执行器。

无 Spring 时才延迟创建内置默认客户端，也可使用 `HttpUtil.setClient(client)` 手工替换。Spring 已绑定时不能用 setClient 改成另一个客户端，防止静态调用和注入分离；请注册 HttpExecutor 或 HttpRequestClient Bean。替换手工客户端不自动关闭旧实例。Spring 关闭后静态入口明确报错，不静默回退到另一套执行器；新的 Spring 上下文重新绑定或显式 setClient 后可继续使用。

### 判断是否为 404

```java
boolean missing = HttpUtil.isNotFound("https://api.example.com/resource");
```

优先 HEAD；仅遇到 405/501（不支持 HEAD）回退 GET，两条路径均不读取正文。只有明确 HTTP 404 返回 true。超时、网络错误和其他状态返回 false，所以 false 不证明存在或健康；非法 URL 等调用参数错误仍抛出异常。少数服务的 HEAD/GET 状态不一致，业务可明确使用 GET 的 status() 检查。

带认证的检查或需要区分其他失败时，使用 status()：

```java
int status = HttpUtil.get("https://api.example.com/resource")
        .bearerToken(token)
        .timeout(Duration.ofSeconds(3))
        .status();
if (status == 404) {
    // 明确不存在；网络失败会抛异常，不伪装成 404。
}
```

`execute()` 的普通 4xx/5xx、网络调用与部分 JSON 解码失败包装为 HttpRequestException；status() 则直接返回 4xx/5xx。参数校验和非法 URI 不保证都包装。204 空响应不当作 JSON 解析。不默认自动重试，不默认跟随重定向，调用仍是同步方式。

## 自定义客户端与生命周期

默认执行器复用一个 JDK HttpClient 的连接池；每次请求只创建轻量超时适配器，请求体阻塞写入使用虚拟线程。Spring 管理的 HttpExecutor Bean 在销毁时关闭自己创建的连接池，注入它的客户端不重复关闭。手动通过 HttpRequestClient.create/withTimeout(builder, mapper, timeout) 创建的客户端拥有默认执行器，不再使用时调用 close()。

保留简单调用：

```java
// 使用当前选中的执行器（包括 Spring 自定义实现），只改变默认响应超时。
try (HttpRequestClient client = HttpUtil.withTimeout(Duration.ofSeconds(10))) {
    client.get("https://api.example.com/a").timeout(Duration.ofSeconds(2)).execute();
    client.get("https://api.example.com/b").timeout(Duration.ofSeconds(5)).execute();
    // 默认实现中两次请求使用同一个连接池；关闭此视图不会关闭共享执行器。
}
```

需要自定义代理、客户端证书、SSL 或连接超时，可以声明自己的 HttpExecutor Bean，默认执行器自动退让，静态与注入入口一起生效。示例方法放入业务的 `@Configuration` 类：

```java
@Bean(destroyMethod = "close")
public java.net.http.HttpClient remoteTransport() {
    return java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            // .proxy(proxySelector)
            // .sslContext(sslContext)
            .build();
}

@Bean
public HttpExecutor projectHttpExecutor(
        RestClient.Builder builder, ObjectMapper mapper,
        HttpRequestProperties properties,
        @Qualifier("remoteTransport") java.net.http.HttpClient transport) {
    return RestClientHttpExecutor.create(builder, mapper, properties, transport);
}
```

所需导入：`org.springframework.context.annotation.Bean`、`org.springframework.beans.factory.annotation.Qualifier`、`org.springframework.web.client.RestClient`、`com.fasterxml.jackson.databind.ObjectMapper`、`com.yulinlin.data.core.http.HttpRequestProperties`、`com.yulinlin.data.core.http.HttpExecutor`、`com.yulinlin.data.core.http.RestClientHttpExecutor` 和 Duration。外部提供的 transport 由业务/容器管理，执行器不会关闭它。

`RestClientHttpExecutor.create(...)`、`HttpRequestClient.create(...)` 和静态 `HttpRequestClient.withTimeout(builder, mapper, timeout)` 明确创建默认实现并安装框架 JDK 请求工厂，不查找 Spring 的自定义执行器；保留 builder 的消息转换器、默认请求头、拦截器等，但**不沿用另一种 requestFactory 的网络设置**。日常业务使用注入的客户端或 HttpUtil；不要通过这些工厂绕过已选定的执行器。

已配置好的 Apache/其他 RestClient 可使用 `new RestClientHttpExecutor(restClient, mapper)` 注册为 Bean，或保留旧写法 `new HttpRequestClient(restClient, mapper)`（第三个参数可提供正文大小限制）。它们完整保留原工厂，但超时必须在原客户端配置。再使用请求 `.timeout(...)` 或超时视图会明确报错，不会悄悄替换代理或证书。

默认执行器内部使用 exchange() 统一处理状态和大小限制，不执行 RestClient 的 defaultStatusHandler；需要该扩展语义的业务直接使用原 RestClient，或在取得工具响应/异常后自行处理。完全更换底层实现的协议、示例和资源归属见[HTTP 执行器规范与替换](http-executor.md)。

用户提供 URL 时校验协议、目的地址与内网访问策略，防止 SSRF；不要无差别记录 token、cookie、密码及错误响应体。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
