# HTTP 请求、上传与下载

[文档首页](../README.md) / [常用工具](README.md)

JSON、表单、文件、超时设置和 404 检查。

## 本页导航

- [HTTP 请求](#http-请求)
- [上传与下载](#上传与下载)
- [超时与错误处理](#超时与错误处理)
- [判断是否为 404](#判断是否为-404)

## HTTP 请求

公共类型在 `com.yulinlin.data.core.http`：HttpRequestClient、HttpRequest、HttpUtil、HttpResponse、HttpFile、HttpRequestException。底层基于 Spring RestClient，调用同步阻塞。

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
| HTTP 方法 | get/post/put/patch/delete；其他方法 request(HttpMethod, url) |
| 查询与头 | query(name, value)、header(name, value)、headers(HttpHeaders) |
| 认证 | basicAuth(user, password)、bearerToken(token) |
| JSON | json(Object) |
| 普通表单 | form(name, value)、form(Map<String,Object>) |
| 其他 body | body(Object, MediaType) |
| 响应 | getStatus、getStatusCode、getHeaders、getBody、getBodyAsString、bodyAs |

query 的 null 值会忽略。同一个请求只使用一种 body 类型，不能混合 json 与 form；请求构造器是可变对象，不跨线程共享。

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

HttpFile 支持 of(Path)、of(filename, byte[], MediaType)、of(filename, InputStream, MediaType)。输入流上传不保证可重放，不重复使用已消费的原流。

execute 会把响应 body 放入内存，大文件优先 downloadTo。下载会创建父目录并覆盖同名文件，失败可能留下部分文件，不保证原子替换；返回响应 body 为空，内容已写磁盘。业务需校验目标路径，不能直接信任远端路径。

## 超时与错误处理

Spring 注入客户端默认 10 秒，配置如下：

```yaml
yulinlin:
  http:
    timeout: 10s
```

单次 `timeout(Duration)` 覆盖默认值，必须为正数。同一 Duration 同时配置连接和响应读取超时，不提供独立链式 connectTimeout/readTimeout；不是覆盖网络、重定向、写盘等全部阶段的严格总截止时间。

静态 HttpUtil 默认也为 10 秒，但不自动读取 Spring 配置。`HttpUtil.withTimeout(duration)` 返回新客户端；`HttpUtil.setClient(client)` 替换静态默认客户端，应在初始化阶段明确配置，不在请求处理中反复切换。

`new HttpRequestClient(restClient, mapper)` 沿用给定客户端设置，不自动添加默认超时。单次 timeout 目前会新建底层客户端，大量相同超时请求优先复用预配置客户端。

### 判断是否为 404

```java
boolean missing = HttpUtil.isNotFound("https://api.example.com/resource");
```

它执行 GET，不是 HEAD；只有明确 HTTP 404 返回 true。超时、网络错误和其他状态返回 false，所以 false 不证明存在或健康。成功响应按普通请求读入内存，不适合检查超大文件。

带认证的检查或需要区分其他失败时，自己处理异常：

```java
import com.yulinlin.data.core.http.HttpRequestException;

try {
    HttpUtil.get("https://api.example.com/resource").bearerToken(token).execute();
} catch (HttpRequestException error) {
    Integer status = error.getStatusCode(); // 网络错误可能为 null。
    if (!Integer.valueOf(404).equals(status)) throw error;
    // 业务处理确实不存在。
}
```

普通 4xx/5xx、网络调用与部分 JSON 解码失败包装为 HttpRequestException；参数校验和非法 URI 不保证都包装。204 空响应不当作 JSON 解析。不假定自动重试、自动跟随重定向或异步能力存在。

用户提供 URL 时校验协议、目的地址与内网访问策略，防止 SSRF；不要无差别记录 token、cookie、密码及错误响应体。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
