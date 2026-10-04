# HTTP 请求、上传与下载

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`core/src/main/java/com/yulinlin/data/core/http/`。若与实际安装版本冲突，以该版本源码为准。

### 5.1 入口、完整包名和同步调用

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

    public RemoteApiService(HttpRequestClient client) {
        this.client = client;
    }

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

所有调用是同步阻塞的。复用 `HttpRequestClient`；每次创建新的 HttpRequest 构造器，不跨线程共享可变请求对象。

### 5.2 常用调用片段

以下片段使用这些 imports，放在业务方法中执行；url、token、Path 等由业务提供：

```java
import com.yulinlin.data.core.http.HttpUtil;
import com.yulinlin.data.core.http.HttpFile;
import com.yulinlin.data.core.http.HttpResponse;
import com.yulinlin.data.core.http.HttpRequestException;
import java.nio.file.Path;
import java.time.Duration;
```

```java
// GET 参数；query 的 null 值被忽略
HttpResponse response = HttpUtil.get("https://api.example.com/users")
        .query("page", 1).query("size", 20)
        .header("X-Request-Id", "demo-request")
        .execute();
String text = response.getBodyAsString();
int status = response.getStatus();

// application/x-www-form-urlencoded
HttpUtil.post("https://api.example.com/forms")
        .form("name", "alice").form("enabled", true).execute();

// multipart/form-data：普通表单项与文件一起上传
HttpUtil.post("https://api.example.com/files")
        .multipart("category", "invoice")
        .file("file", HttpFile.of(Path.of("upload/invoice.pdf")))
        .execute();

// 流式下载；路径应由业务校验，不能直接信任远端传入的文件路径
HttpUtil.get("https://api.example.com/export")
        .timeout(Duration.ofSeconds(60))
        .downloadTo(Path.of("download/export.zip"));
```

常用签名：

- `get/post/put/patch/delete(String)`，其他方法用 `request(org.springframework.http.HttpMethod, String)`。
- `json(Object)`、`form(String,Object)`、`form(Map<String,Object>)`、`multipart(String,Object)`、`file(String,HttpFile)`。
- 文本/XML/二进制用 `body(Object, org.springframework.http.MediaType)`。
- `basicAuth(String,String)`、`bearerToken(String)`、`header(String,String)`。
- `HttpFile.of(Path)`、`HttpFile.of(String, byte[], MediaType)`、`HttpFile.of(String, InputStream, MediaType)`。
- 响应：`getStatus()` 返回 int；`getStatusCode()` 返回 Spring HttpStatusCode；`getBody()` 返回 byte[]；`bodyAs(Class<T>)` 或 Jackson `TypeReference<T>` 解析 JSON。

同一个请求只允许一种 body 类型。不要混用 `.json()` 与 `.form()`；文件上传的普通字段用 `.multipart()`，不要用 `.form()`。输入流上传不可直接重复使用原流。

### 5.3 超时和复用

- Spring 自动配置客户端默认 10 秒，可由 `yulinlin.http.timeout` 配置；单次 `.timeout(Duration)` 覆盖默认值，必须为正数。
- 静态 HttpUtil 初始默认也为 10 秒，但不会自动读取 Spring 配置。`HttpUtil.withTimeout(Duration)` 返回新客户端，不会更改静态默认客户端。
- `HttpUtil.setClient(client)` 可替换全局默认客户端；若需要，只在初始化阶段明确设置，不建议请求期间动态切换。
- `new HttpRequestClient(restClient, mapper)` 直接使用传入客户端，并不自动添加 10 秒配置。
- 当前工厂把同一 Duration 设置到 JDK HttpClient 的 connectTimeout 与 Spring JdkClientHttpRequestFactory 的 readTimeout。不要将其描述成独立的 `.connectTimeout()` / `.readTimeout()` 公共链式 API，也不要保证它是覆盖所有网络与落盘阶段的严格总截止时间。
- 单次 `.timeout()` 当前会构造新的底层 HttpClient；大量同超时请求应优先复用预配置的客户端，减少重复建连接池的机会。

### 5.4 404、异常与下载边界

```java
boolean explicitlyMissing = HttpUtil.isNotFound("https://api.example.com/resource");
```

该方法实际执行 GET，不是 HEAD；只有捕获到 HTTP 404 返回 true。超时、网络错误及其他状态返回 false，因此 false 不代表资源存在或服务健康。成功响应会按普通请求读取内容，不适合探测超大文件；带认证和特殊头的检查应自行构造请求并捕获状态。

```java
try {
    HttpUtil.get("https://api.example.com/resource").execute();
} catch (HttpRequestException e) {
    Integer statusCode = e.getStatusCode(); // 网络错误可能是 null
    if (Integer.valueOf(404).equals(statusCode)) {
        // 业务处理资源不存在
    } else {
        throw e; // 不把其他失败伪装成成功
    }
}
```

普通 4xx/5xx 与网络调用失败包装为 HttpRequestException；JSON 解码失败也可能包装成该异常。参数校验、非法 URI 等其他异常不保证都被包装。不要解析空的 204 响应为 JSON。

`execute()` 将响应读入内存。大文件用 `downloadTo(Path)`，它会创建父目录并覆盖同名文件；下载失败可能留下部分文件，不保证原子替换。下载返回的 HttpResponse body 为空，内容在磁盘。默认不要假设自动重试、自动跟随重定向或异步能力存在。

对于用户提供的 URL，业务层要校验协议和目的地址，防止 SSRF；Token、Cookie、密码、响应错误体不要无差别记录。
