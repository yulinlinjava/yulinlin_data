# HTTP 执行器规范与替换

[文档首页](../README.md) / [常用工具](README.md) / [HTTP 基础用法](http.md)

统一调用方式，默认使用现有 RestClient 实现；业务方只替换 HttpExecutor，不重写链式工具类。类型均位于 `com.yulinlin.data.core.http`，第一版继续复用 Spring 的 HttpMethod、HttpHeaders、MediaType，不额外增加一套同义类型。

## 本页导航

- [组件边界](#组件边界)
- [实现协议](#实现协议)
- [Spring 自动替换](#spring-自动替换)
- [请求与响应适配](#请求与响应适配)
- [非 Spring 与生命周期](#非-spring-与生命周期)
- [维护检查清单](#维护检查清单)

## 组件边界

```text
HttpUtil ──────────┐
                  ↓
注入 HttpRequestClient → HttpRequest → HttpRequestData → HttpExecutor
                                                        ├─ 默认 RestClientHttpExecutor
                                                        └─ 用户实现
```

| 组件 | 职责 |
| --- | --- |
| HttpRequestClient | 合并默认配置、创建请求、统一 404 策略及响应 ObjectMapper；不直接访问网络 |
| HttpRequest | 组装 query、header、JSON、form、multipart、timeout；不跨线程共享 |
| HttpRequestData | 只读请求快照；不依赖 RestClient，也不暴露其请求对象 |
| HttpExecutor | 真正执行网络请求，负责连接复用、流、超时、大小限制和下载安全 |
| RestClientHttpExecutor | 默认适配器，承接已有优化；支持直接包装外部 RestClient |
| HttpAutoConfiguration / HttpClientBinding | 默认 Bean 退让、选择实现、绑定和解除静态入口 |

暂不增加执行器路由、插件注册中心、自动重试或多套 HTTP 客户端配置组。单个应用选择一个默认执行器即可。

## 实现协议

```java
public interface HttpExecutor extends AutoCloseable {
    HttpResponse execute(HttpRequestData request);
    int status(HttpRequestData request);
    HttpResponse download(HttpRequestData request, Path destination);
    default void close() { }
}
```

| 方法/行为 | 要求 |
| --- | --- |
| execute | 只在正文上限内读入内存；4xx/5xx 抛 HttpRequestException，错误正文按错误上限截断 |
| status | 返回状态码，包括 4xx/5xx；不读取完整正文；网络失败抛异常，不当作 404 |
| download | 流式写同目录临时文件，完整成功才替换目标；失败清理临时文件；返回空响应正文 |
| 超时 | 遵守请求快照中的响应超时，不能影响其他并发请求；不支持时明确报错，不静默忽略 |
| 资源 | 单例线程安全、复用连接池；关闭响应流，不为了复用连接强制读完大错误正文 |
| 重试/跳转 | 默认不自动重试、不自动跟随重定向；不要重复提交 POST 或一次性文件流 |
| close | 只释放实现自己拥有的资源；允许重复关闭，不关闭外部共享客户端 |

`download` 必须拒绝 HEAD、无正文状态、3xx/4xx/5xx、206 或 Content-Range 等非完整文件响应；提供 Content-Length 时校验读取量。优先原子替换，不支持时可退回完整下载后的普通移动，不承诺断电安全。不要把 download/status 默认实现成 execute，否则会把大正文读入内存。

`HttpRequestClient` 会对普通请求返回的错误状态、超大正文做防御性检查，但这不能替代执行器在读取流时限制内存，也不能撤销一个错误执行器已经覆盖的文件。第三方实现需要遵守协议。

404 判断始终在 HttpRequestClient：先 HEAD，405/501 才回退 GET；不要求每个执行器各写一套 isNotFound。具体默认超时和大小配置见[HTTP 基础用法](http.md#超时与错误处理)。

## Spring 自动替换

业务提供一个 Bean 即可，Bean 名字不作要求：

```java
@Bean
public HttpExecutor projectHttpExecutor() {
    return new MyHttpExecutor(); // 业务实现三个执行方法，遵守上面的协议
}
```

没有自定义 Bean 时自动注册 RestClientHttpExecutor；有自定义 HttpExecutor 时默认实现不创建。自动创建的 HttpRequestClient 注入这个执行器。存在多个实现时必须指定 `@Primary`，否则启动失败并提示原因。若业务直接提供 HttpRequestClient Bean，则以该客户端为准；多个客户端同样需要 @Primary。

Spring 单例初始化完成后，两种入口保持一致：

```java
@Service
public class RemoteService {
    private final HttpRequestClient client;
    public RemoteService(HttpRequestClient client) { this.client = client; }

    public void invoke(String url) {
        client.get(url).timeout(Duration.ofSeconds(3)).execute();
        HttpUtil.get(url).timeout(Duration.ofSeconds(3)).execute();
        // client == HttpUtil.getClient()，两次走同一个执行器。
        HttpUtil.withTimeout(Duration.ofSeconds(5)).get(url).execute();
        // 仍走同一个执行器，只改变该视图的默认响应超时。
    }
}
```

`@Bean`、`@Service` 分别导入 Spring 的对应注解，Duration 导入 java.time.Duration。业务初始化回调中直接注入客户端，不依赖静态入口的绑定时机。Spring 激活绑定后，setClient 不允许换成另一个实例；应用关闭后静态调用报错，不静默切回默认实现。

如果只是统一请求头、代理或 SSL，无须重新实现协议：

```java
@Bean
public HttpExecutor projectHttpExecutor(
        RestClient.Builder builder, ObjectMapper mapper, HttpRequestProperties properties) {
    return RestClientHttpExecutor.create(
            builder.clone().defaultHeader("X-Client", "video-service"), mapper, properties);
}
```

所需导入：`org.springframework.web.client.RestClient`、`com.fasterxml.jackson.databind.ObjectMapper` 和本包的 HttpExecutor、RestClientHttpExecutor、HttpRequestProperties。传入自定义 JDK HttpClient 的完整 SSL/代理配置案例见[自定义客户端与生命周期](http.md#自定义客户端与生命周期)。

真正更换网络库时实现 HttpExecutor 三个方法即可；无需继承默认实现。需要委托默认实现增强时，可以在自己的执行器中持有 RestClientHttpExecutor，并将 execute/status/download 委托给它；自建的委托实例也要在 close 中关闭。

## 请求与响应适配

HttpRequestData 通过 getter 读取：

| 信息 | API / 说明 |
| --- | --- |
| 方法和 URL | getMethod()、getUri()；URL 已编码，不再转义或重排 query，可能使用客户端的相对地址 |
| 请求头 | getHeaders()；深复制列表并只读，不允许直接修改快照 |
| 正文 | getBodyKind()：NONE / JSON / FORM / MULTIPART / RAW；getBody()、getContentType() |
| 普通表单 | getForm()，保留同名多值 |
| multipart | getMultipart()，值为 String 或 HttpFile，保留同名多个文件 |
| 超时 | getTimeout() 是有效 Duration；isTimeoutOverridden() 表示用户显式覆盖（含超时视图） |
| 大小上限 | getMaxResponseSize()、getMaxErrorSize()，单位均为字节 |

`HttpRequest.toRequestData()` 只生成快照，不执行请求。快照复制头、表单与 multipart 容器；正文对象、字节数组、文件内容和输入流不默认深度克隆。提交期间不要并发修改正文；输入流文件只能消费一次。

HttpFile 提供公开适配入口：

```java
String filename = file.getFilename();
MediaType type = file.getContentType(); // 可以为 null
long length = file.getContentLength(); // 未知为 -1，不会提前消费输入流
boolean repeatable = file.isRepeatable();
try (InputStream input = file.openStream()) {
    // 交给底层网络库发送；不要为计算长度先读完。
}
// Spring 适配器也可直接使用 file.getResource()。
```

自定义执行器创建响应和异常：

```java
// status、headers、body 来自底层网络库；body 必须已按上限读取。
return HttpResponse.of(status, headers, body);
```

```java
throw new HttpRequestException(
        request.getMethod().name(), request.getUri().toString(),
        status, errorBody, truncated, cause);
// 网络失败的 status 可为 null；不要把网络错误伪装成 HTTP 404。
```

`HttpResponse.of(...)` 防御性复制外部正文和头；客户端返回响应前关联自己的 ObjectMapper，因此 `bodyAs(Class)` / `bodyAs(TypeReference)` 在静态、注入和自定义执行器路径上使用一致的应用配置。必要时直接调用执行器也可用 `of(status, headers, body, mapper)` 指定 mapper。

直接包装外部 RestClient 时保留其网络配置；默认 timeout 不强制覆盖它，显式 timeout/超时视图会报错，因为框架不能安全修改第三方工厂。其他自定义执行器也不得静默忽略调用方显式提出但不支持的能力。

## 非 Spring 与生命周期

```java
HttpExecutor executor = new MyHttpExecutor();
HttpRequestClient client = new HttpRequestClient(executor);
HttpUtil.setClient(client);
// 所有静态请求走 executor；用完后由其所有者关闭 executor。
```

需要 JSON 定制及全局默认值时使用 `new HttpRequestClient(executor, objectMapper, properties)`。

| 创建方式 | 谁关闭执行器 |
| --- | --- |
| HttpExecutor Bean + 自动 HttpRequestClient | Spring 关闭执行器 Bean 一次；客户端不重复关闭 |
| new HttpRequestClient(executor, ...) | 外部提供者负责；client.close() 不关闭共享执行器 |
| HttpRequestClient.create / 静态 withTimeout(builder, mapper, duration) | 创建的是默认执行器，client.close() 关闭它；这类显式工厂不查找 Spring Bean |
| client.withTimeout(duration) / HttpUtil.withTimeout(duration) | 轻量视图不拥有执行器，close 不影响原客户端 |
| 外部传入的 JDK HttpClient 或 RestClient | 外部所有者负责，默认执行器不会关闭它 |

静态入口适用于单个主应用上下文。第二个上下文不能抢占已有绑定，会明确报错；多上下文隔离应用应在额外上下文排除 HttpAutoConfiguration，自行注册并注入 HttpRequestClient，仅主上下文管理静态入口。关闭一个未取得绑定的上下文不会清除主上下文的绑定。

## 维护检查清单

- 新增底层实现只实现 HttpExecutor；不要让 HttpRequest 再依赖 RestClient 或 SDK 对象。
- 不在每次请求中创建连接池，也不按无限多超时值缓存客户端。
- 测试普通请求、错误截断、未知长度上传、状态检查、下载失败保护、并发超时和 close 所有权。
- 测试自定义 Bean 退让、@Primary、静态/注入一致、关闭后解绑；初始化阶段优先注入。
- 测试源码放在 admin/src/test。新增测试不代表已执行；遵守用户关于不编译、不运行测试和不运行 YAML 属性绑定测试的要求。

---

下一步：[HTTP 基础用法](http.md) / [工具目录](README.md)。
