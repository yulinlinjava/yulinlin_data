# yulinlin-data：外部 AI 单文件接入指南

---

> 自动生成，请勿直接编辑。维护源为 doc/topics/，生成命令：./doc/build-ai-docs.ps1。

---

用途：上传一个文件给外部 AI。已包含当前全部专题；无需再上传相同专题、旧案例或性能报告。制品版本 3.0；JDK 25；Spring Boot 3.5。示例的验证范围见各专题。

---

阅读顺序：上下文 → 按任务阅读 ORM/事务、HTTP、反射或其他工具 → 排障与交付检查。示例地址、表名、账号均为占位，执行写操作前必须按业务确认。

---

<!-- source: doc/topics/00-context.md -->
## 项目上下文与生成代码约定

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`pom.xml`；各模块 `pom.xml`。若与实际安装版本冲突，以该版本源码为准。

1. 这是自定义 ORM，不是 MyBatis-Plus、JPA 或 Spring Data。不要生成 `BaseMapper`、`LambdaQueryWrapper`、`@Entity` 等其他框架 API 来替代本文接口。
2. 优先使用本文明确列出的入口、包名和签名。未列出的高级功能应查当前源码，不根据其他框架同名方法猜测。
3. 主键用 `@JoinMeta(primaryKey = true)`；不要沿用旧文档中的 `@JoinPrimary`。
4. 先确认表结构、实体基类、主键、筛选条件及会话，再生成写入代码。不要生成无条件更新或删除，也不要假设框架有全表写入拦截。
5. ORM 需要 Spring 上下文初始化完成、可用数据库会话与已存在的表；不要在静态初始化块中查询数据库。框架不负责根据下文实体自动建表。
6. 深克隆与 Bean 到 DTO 的类型映射不是同一能力。HTTP 的“不是 404”与“请求成功”也不是同一含义。
7. 编码之前确认实际依赖包含接口；如果缺少类或方法，先核对版本和运行时类路径，不用反射或异常吞掉掩盖版本错配。

### 模块与入口

| 需求 | 模块 | 准确入口 |
| --- | --- | --- |
| Spring Boot + MySQL ORM | starter + mysql | `com.yulinlin.common.domain.IdEntity` / `SuperEntity` |
| 实体映射 | core | `com.yulinlin.data.core.anno.JoinTable`、`JoinField`、`JoinMeta`、`JoinWhere` |
| 数据库分页结果 | lang | `com.yulinlin.data.lang.util.Page`，不是 Spring Data Page |
| HTTP | core | `com.yulinlin.data.core.http.HttpRequestClient`、`HttpUtil` |
| HTTP 响应/异常/文件 | core | 同包下 `HttpResponse`、`HttpRequestException`、`HttpFile` |
| 反射与深克隆 | lang | `com.yulinlin.data.lang.reflection.ReflectionUtil` |
| JSON | lang | `com.yulinlin.data.lang.json.JsonUtil` |
| 字符串、日期 | lang | `com.yulinlin.data.lang.util.StringUtil`、`DateTime` |
| 树与 ID | common | `com.yulinlin.common.util.TreeUtil`、`SnowflakeUtil` |
| Web 响应包装 | starter | `com.yulinlin.starter.domain.R` |

MySQL 是本文完整示例路径。仓库存在其他数据库模块，不代表本文示例与事务行为已在所有数据库验证。admin 是示例/测试宿主，不是业务接入所需依赖。

---

<!-- source: doc/topics/10-orm.md -->
## ORM 接入与 CRUD

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`common/.../domain/IdEntity.java`、`SuperEntity.java`；`core/.../model/`；`mysql/.../MysqlParseAutoConfig.java`。若与实际安装版本冲突，以该版本源码为准。

#### 3.1 ORM 项目

使用 JDK 25，业务项目采用 Spring Boot 3.5 系列依赖管理：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mysql</artifactId>
    <version>3.0</version>
</dependency>
```

不要假设这些制品已发布 Maven Central；应使用团队制品仓库，或先从同一份源码安装相关模块。core/lang 等同组模块应保持同一构建来源。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
yulinlin:
  http:
    timeout: 10s
```

当前 core、starter、mysql 等模块提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。在正常 Boot 自动配置链中无需额外的框架启用注解。MySQL 自动配置依赖 DataSource，并创建会话名 `primary`、Bean 名 `jdbcSession`。仅引入 starter 不会创建 MySQL 数据库会话。

只需反射/JSON 时可依赖 `com.yulinlin:lang:3.0`。只需 HTTP 时可依赖 `com.yulinlin:core:3.0`；但 core 在 Boot 中还包含 ORM 相关自动配置，不是一个专门拆分的纯 HTTP starter。项目已引入 starter 时无需重复声明 core。

#### 3.2 最小实体：避免隐式时间字段

下面以 `IdEntity` 为基类，仅继承 String 类型 id 和主键生成逻辑。一个 public 类放在对应的独立 `.java` 文件中。

```java
package demo.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable("ai_demo_user")
public class DemoUser extends IdEntity<DemoUser> {
    @JoinField(name = "user_name")
    @JoinWhere
    private String username;

    @JoinField
    @JoinWhere
    private Integer status;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

匹配上述模型的示例建表 SQL（仅在自己的演示库执行）：

```sql
CREATE TABLE ai_demo_user (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_name VARCHAR(100),
    status INT
);
```

`SuperEntity<T>` 在 `IdEntity<T>` 上增加 `crtTime`、`uptTime` 及插入/更新前填充逻辑；选择它时要让表结构与这些实际映射字段一致，不能直接套用上面的三列表。

注解规则：

- `@JoinTable` 标记表，`@JoinField(name = "...")` 指定列，`@JoinField(exist = false)` 排除字段。
- 不要假设没有 `@JoinField` 的字段一定会被忽略；当前工厂会处理一些未注解字段。非持久化字段应显式排除。
- 自定义主键建议同时标记 `@JoinField`、`@JoinMeta(primaryKey = true)`、`@JoinWhere`，不要仅依赖一个主键注解覆盖所有工厂行为。
- `@JoinWhere` 参与按对象构造条件；显式 `.eq(...)` 等链式条件可直接使用。
- 主键属性名与数据库列名不一致时，必须额外检查删除等工厂的映射行为，本文不承诺所有路径都正确处理重命名主键。

### CRUD 服务示例

```java
package demo.service;

import demo.domain.DemoUser;
import org.springframework.transaction.annotation.Transactional;
import com.yulinlin.data.lang.util.Page;
import org.springframework.stereotype.Service;

@Service
public class DemoUserService {
    public DemoUser findById(String id) {
        requireId(id);
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getId, id).selectOne();
    }

    public Page<DemoUser> page(int pageNumber, int pageSize) {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("Invalid page parameters");
        }
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getStatus, 1)
                .orderByDesc(DemoUser::getId)
                .selectPage(pageNumber, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public String create(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        DemoUser user = new DemoUser();
        user.setUsername(username);
        user.setStatus(1);
        user.createInsertWrapper().execute();
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(String id, int status) {
        requireId(id);
        DemoUser patch = new DemoUser();
        patch.setId(id);
        patch.setStatus(status);
        return patch.createUpdateWrapper().execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteById(String id) {
        requireId(id);
        DemoUser target = new DemoUser();
        target.setId(id);
        return target.createDeleteWrapper().execute();
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
    }
}
```

方法约定：

| API | 语义 |
| --- | --- |
| `createSelectWrapper().selectList()` | 返回 `List<E>` |
| `selectOne()` | 按第 1 页、每页 1 条查询，不是“多条即抛异常” |
| `selectPage(1, 20)` | 页码从 1 开始，返回框架 `Page<E>` |
| `Page.getList()` / `getTotal()` | 数据列表 / 总数；当前 total 是 int |
| `count()` / `exist()` | 计数 / 存在判断 |
| `createInsertWrapper().execute()` | 执行插入；IdEntity 未设置 id 时生成字符串 ID |
| `createUpdateWrapper().execute()` | 根据模型构造更新；当前工厂跳过 null 字段 |
| `createDeleteWrapper().execute()` | 根据模型主键构造删除；业务侧先校验主键 |

条件可用 `eq/ne/gt/gte/lt/lte/like/likeRight/between/in/nin/isNull`。不要把其他库的 `ge/le/notIn` 名称直接搬过来。复杂嵌套条件先确认 `ModelConditionWrapper` 和 `IConditionWrapper` 的实际签名。

将 patch 字段设为 null 不代表会生成 `SET column = NULL`；清空字段需求必须核查底层字段构造器生成的 SQL。不要把整个 HTTP 请求 DTO 不加限制地复制进更新实体，防止越权更新 id 或敏感字段。

事务说明请同时读取 `20-transactions.md`；上面的服务使用 Spring `@Transactional`。多表、级联等历史案例不属于已核验的完整接入示例。

---

<!-- source: doc/topics/20-transactions.md -->
## Spring 事务兼容与边界

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`jdbc/src/main/java/com/yulinlin/jdbc/aop/SpringTransactionAop.java`；`jdbc/src/main/java/com/yulinlin/jdbc/session/ConnectionUtil.java`。若与实际安装版本冲突，以该版本源码为准。

框架已接入 Spring `org.springframework.transaction.annotation.Transactional`，不要求业务只能使用 `@JoinTransaction`。Spring Boot 常规单数据源、同步 JDBC 场景优先按上例使用 Spring 注解，并保证事务管理器管理同一个 DataSource。

源码依据：jdbc 自动配置 `DataJdbcApplication` 注册 `SpringTransactionAop`，识别方法或类上的 Spring `@Transactional`，同步调用 route 的 start/commit/rollback；同步 JDBC 调用经 `ConnectionUtil` 获取和释放连接，在其单路由分支使用 `DataSourceUtils`，参与 Spring 绑定的连接事务。

需要 Spring 代理生效；`new Service()` 或同对象内部直接调用不能当作有效代理事务示例。不要同时叠加两种事务注解作为默认用法。

兼容边界：当前框架切面本身不解析 propagation、isolation、rollbackFor 等注解属性；Spring 事务拦截器会负责其自身事务属性，但不能据此保证框架自管连接路径与 Spring 的所有语义一致。`ConnectionUtil` 在 `loadBalanceList().size() > 1` 时使用自有连接池，异步更新也走自有连接路径。多路由、异步、REQUIRES_NEW/嵌套事务、noRollbackFor 等场景需另做集成验证，不承诺自动跨数据源原子提交。

另外保留框架注解 `com.yulinlin.data.core.anno.JoinTransaction`。其 `String[] value()` 当前没有被切面读取，不要依靠该参数选择事务会话。两种注解不是可以无条件互换的全部事务语义实现。

### 标准业务方法写法

```java
import org.springframework.transaction.annotation.Transactional;

// 放在 Spring 管理的 Service public 方法上，并通过代理调用。
@Transactional(rollbackFor = Exception.class)
public void saveBusinessData() {
    // 在这里执行本次业务需要的 ORM 操作
}
```

本专题核对了注解与连接接入路径，没有执行事务回滚集成测试。验收应至少覆盖正常提交、异常回滚、同类自调用，以及项目实际使用的传播行为。

---

<!-- source: doc/topics/30-http.md -->
## HTTP 请求、上传与下载

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`core/src/main/java/com/yulinlin/data/core/http/`。若与实际安装版本冲突，以该版本源码为准。

#### 5.1 入口、完整包名和同步调用

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

#### 5.2 常用调用片段

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

#### 5.3 超时和复用

- Spring 自动配置客户端默认 10 秒，可由 `yulinlin.http.timeout` 配置；单次 `.timeout(Duration)` 覆盖默认值，必须为正数。
- 静态 HttpUtil 初始默认也为 10 秒，但不会自动读取 Spring 配置。`HttpUtil.withTimeout(Duration)` 返回新客户端，不会更改静态默认客户端。
- `HttpUtil.setClient(client)` 可替换全局默认客户端；若需要，只在初始化阶段明确设置，不建议请求期间动态切换。
- `new HttpRequestClient(restClient, mapper)` 直接使用传入客户端，并不自动添加 10 秒配置。
- 当前工厂把同一 Duration 设置到 JDK HttpClient 的 connectTimeout 与 Spring JdkClientHttpRequestFactory 的 readTimeout。不要将其描述成独立的 `.connectTimeout()` / `.readTimeout()` 公共链式 API，也不要保证它是覆盖所有网络与落盘阶段的严格总截止时间。
- 单次 `.timeout()` 当前会构造新的底层 HttpClient；大量同超时请求应优先复用预配置的客户端，减少重复建连接池的机会。

#### 5.4 404、异常与下载边界

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

---

<!-- source: doc/topics/40-reflection.md -->
## 反射与深复制

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`lang/src/main/java/com/yulinlin/data/lang/reflection/`。若与实际安装版本冲突，以该版本源码为准。

下例的 `demo.domain.DemoUser` 定义在 `10-orm.md`；只使用反射工具时可换成自己的有无参构造器、可写属性的普通 Bean，不需要启动 ORM。

```java
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import demo.domain.DemoUser;

DemoUser user = ReflectionUtil.newInstance(DemoUser.class);
ReflectionUtil.invokeSetter(user, "username", "alice");
Object name = ReflectionUtil.invokeGetter(user, "username");

ReflectionUtil.PropertyAccess access = ReflectionUtil.property(DemoUser.class, "username");
access.set(user, "bob");
Object value = access.get(user);

DemoUser clone = ReflectionUtil.deepClone(user);
DemoUser target = new DemoUser();
ReflectionUtil.copyProperties(user, target);
```

| API | 当前语义 |
| --- | --- |
| `newInstance(Class<E>)` | 需要可访问的无参构造器 |
| `invokeGetter` / `invokeSetter` | 按名字访问，支持嵌套路径；中间对象不要假设会自动创建 |
| `invokeMethod(obj, name, args...)` | 按方法名和参数解析；歧义报错，变长参数需传声明的数组 |
| `property(Class, String)` | 单一属性访问器，可缓存复用，不是嵌套路径解析器 |
| `clone(value)` | 浅复制，不能用于保证嵌套对象独立 |
| `deepClone(value)` / `clone(value, true)` | 对象图深复制；null 输入返回 null |
| `copyProperties(source, target)` | 同名属性深复制到已有目标；跳过源缺少的属性及源 null 值 |

深复制包括 Bean、集合元素、Map 键值、数组及支持的可变值类型；支持范围内保留循环与共享引用。每次 deepClone 都有独立身份跟踪上下文，这是防止不同操作污染，不应直接改成共享单例。

重要边界：

- Bean 需要无参构造器及可写属性；record、不可写 final 字段、线程/流/连接等资源对象不作为通用克隆目标。
- 不自动把源嵌套实体转为不同类型 DTO，也不自动转换集合泛型。
- 不可变值可复用；集合包装器可能变为标准可变集合，不保证保留不可修改或同步包装语义。有序集合复用比较器。
- copyProperties 失败时目标可能已部分修改，不提供回滚。源 null 跳过语义也不等于数据库“设为 NULL”。
- 私有成员访问仍受 Java 模块 opens 限制；不是能绕过任意访问控制。
- 当前基于 MethodHandle；ReflectAsmUtil 是弃用兼容入口，新代码不要使用。Kryo 仅出现在 admin 性能对比中，不是 lang 的深克隆后端。
- 整体 deepClone(list) 保留跨元素共享引用；逐条克隆不能保留跨调用共享关系。不要仅为速度交换这两种语义。

---

<!-- source: doc/topics/50-utilities.md -->
## JSON 与其他工具

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`lang/src/main/java/com/yulinlin/data/lang/json/JsonUtil.java`；`lang/.../util/`；`common/.../util/`。若与实际安装版本冲突，以该版本源码为准。

下例的 `demo.domain.DemoUser` 定义在 `10-orm.md`，也可换成自己的普通 Bean。

#### JSON

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.lang.json.JsonUtil;
import demo.domain.DemoUser;
import java.util.List;

String json = JsonUtil.toJson(new DemoUser());
DemoUser user = JsonUtil.parseJson(json, DemoUser.class);
List<DemoUser> users = JsonUtil.parseJson("[]", new TypeReference<List<DemoUser>>() {});
```

`JsonUtil.to(source, Target.class)` 经 JSON 转换，受 Jackson 注解/配置影响，不是对象图克隆。不要依赖它保留循环引用与对象身份。JsonUtil 默认有自有 mapper；starter 会将它设置为 Spring ObjectMapper，不能把默认独立模式配置当作所有环境的配置。HttpUtil 静态默认 mapper 又是独立配置，不会自动和 JsonUtil 同步。

#### 常用入口和注意点

| 类 | 使用示例 / 签名 | 边界 |
| --- | --- | --- |
| `StringUtil` | `isNull(text)`、`isNotNull(text)` | 只判断 null/空串，不等于 isBlank；纯空格不是空串 |
| `StringUtil` | `javaToColumn(name)`、`columnToJava(name)` | 字符串转换工具，不代表所有 ORM 路径自动采用此规则 |
| `DateTime` | `DateTime.now()`、`DateTime.parse(text, "yyyy-MM-dd HH:mm:ss")` | 框架自有日期类型；不要当作不可变 java.time 类型 |
| `Page` | `Page.of(list)`、`Page.page(list, 1, 20)` | 后者是内存分页，不执行 SQL；调用前校验正数页码/页大小 |
| `SnowflakeUtil` | `nextIdStr()`、`nextId()` | 默认节点配置含随机因素，多实例唯一性需显式规划，不能承诺随机节点绝不冲突 |
| `TreeUtil` | `buildTree(List<E>)`，E 实现 `com.yulinlin.common.domain.ITreeNode` | 原地给父节点追加子节点；先清理旧 children，校验重复 id/环，缺失父节点的节点会成为根 |
| `R` | `R.newInstance(data)` | 统一响应包装不是 HTTP 状态设置器；不要猜测存在 `R.ok()` / `R.success()` |

缓存、线程池、锁、金额、事件、服务基类等工具还存在于源码中，但本文未逐个验证其业务契约。需要时先读对应类，不仅凭类名生成调用，更不要把金额精度、分布式锁或线程安全能力自行补全。

---

<!-- source: doc/topics/90-troubleshooting.md -->
## 排障、交付检查与提示模板

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：对应专题列出的源码。若与实际安装版本冲突，以该版本源码为准。

| 现象/需求 | 处理 |
| --- | --- |
| `NoSuchMethodError: ReflectionUtil.property(...)` | 编译时与运行时 JAR 不一致；检查 lang/core 的来源，统一构建和依赖树，不先归咎于 JDK 25 反射 |
| 没有可用会话 | 检查 mysql 模块、DataSource、自动配置是否被排除以及 Spring 初始化顺序 |
| HTTP 超时配置不生效 | 区分 Spring 注入客户端、HttpUtil 静态客户端、手动构造客户端和单次覆盖 |
| `isNotFound()` 返回 false | 不足以认定成功，必要时执行请求并检查状态/异常 |
| DTO 复制类型不兼容 | 显式映射，不把 copyProperties 当作任意类型转换器 |
| 深克隆私有/final/record 报错 | 按支持边界调整模型或选择适当映射方案，不静默忽略字段 |
| 需要“最快的克隆” | 用真实模型同机 JMH，比较相同引用语义；不能宣称某工具总是最快 |

生成代码交付前检查：准确 import、实际依赖版本、表字段、无参构造器、主键/where、代理事务、分页限制、HTTP 超时/鉴权/异常、大文件流式下载、复制语义及敏感数据处理。没有执行过的构建或测试要明确标注为未验证。

### 提供给外部 AI 的提示模板

```text
请基于附件《AI接入指南》为 yulinlin-data 3.0 生成代码，运行环境为 JDK 25 + Spring Boot 3.5。
仅使用指南中已确认的 API；不要套用 MyBatis-Plus/JPA 的接口。
我的需求：[填写业务动作]
表结构与实体基类：[填写建表 SQL、IdEntity 或 SuperEntity]
写操作的定位条件与事务边界：[填写]
HTTP 接口、认证、请求/响应样例：[如涉及则填写，不提供真实密钥]
复制需求：[浅复制/同类型深克隆/不同 DTO 映射，是否需要保留共享引用]
请输出准确 imports、配置、代码和验证步骤；信息不足时先列出缺失项。
不要宣称已执行未实际运行的测试；遇到指南外接口先核查当前源码。
```
