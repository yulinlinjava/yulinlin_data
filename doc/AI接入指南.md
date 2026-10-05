# yulinlin-data：外部 AI 单文件接入指南

---

> 自动生成，请勿直接编辑。维护源为 doc/topics/，生成命令：./doc/build-ai-docs.ps1。

---

用途：上传一个文件给外部 AI。已包含当前全部专题；无需再上传相同专题、旧案例或性能报告。制品版本 3.0；JDK 25；Spring Boot 3.5。示例的验证范围见各专题。

---

阅读顺序：上下文 → 按任务阅读 ORM/级联/事务、HTTP、反射或其他工具 → 排障与交付检查。示例地址、表名、账号均为占位，执行写操作前必须按业务确认。

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
5. ORM 需要 Spring 上下文初始化完成、可用数据库会话与已存在的表；不要在静态初始化块中查询数据库。SQLite 可显式开启实体扫描创建缺失表（见 SQLite 专题）；其他路径不要假设自动建表。
6. 深克隆与 Bean 到 DTO 的类型映射不是同一能力。HTTP 的“不是 404”与“请求成功”也不是同一含义。
7. 编码之前确认实际依赖包含接口；如果缺少类或方法，先核对版本和运行时类路径，不用反射或异常吞掉掩盖版本错配。

### 模块与入口

| 需求 | 模块 | 准确入口 |
| --- | --- | --- |
| Spring Boot + MySQL ORM | starter + mysql | `com.yulinlin.common.domain.IdEntity` / `SuperEntity` |
| Spring Boot + SQLite ORM | starter + sqlite | 默认启用，file 默认 data/local.db，组 sqlite；沿用相同实体与 Wrapper |
| Spring Boot + PostgreSQL ORM | postgresql；common 或 starter 按需引入 | `postgresqlSessionFactory` 创建 PostgresqlSession，继承公共 JdbcSession 与相同 Wrapper；见 PostgreSQL 专题 |
| 实体映射 | core | `com.yulinlin.data.core.anno.JoinTable`、`JoinField`、`JoinMeta`、`JoinWhere` |
| 级联与代理 | core | 同注解包下 `JoinQuery`、`JoinLazy`、`JoinSync`；`com.yulinlin.data.core.session.SessionUtil.route()` |
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

多个数据库的配置、`JdbcSessionFactory.create` 注册及选库，见同目录 `15-datasources.md`（单文件指南已包含该专题）。

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

当前 core、starter、mysql 等模块提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。在正常 Boot 自动配置链中无需额外的框架启用注解。MySQL 模块自行注册 mysqlSessionFactory，并直接创建 `mysqlSession`，默认会话组为 `mysql`，不检查或识别 JDBC URL。公共 JDBC 自动配置只提供通用组件，不选择工厂或创建默认会话。仅引入 starter 不会创建 MySQL 数据库会话；PostgreSQL 接入和驱动要求见 17-postgresql 专题。此默认会话注册与命名于 2026-10-05 更新，本轮未运行测试、编译或打包。

下方省略 group 的示例以只有一个会话组为前提。MySQL、PostgreSQL、SQLite 的默认组分别是 mysql、postgresql、sqlite；同时存在多个组时可配置 yulinlin.datasource.default-group，或通过 Model Wrapper 的第一个参数和 @JoinSession 明确选组。primary 只是普通组名，不再自动优先；需要兼容旧组时显式把 default-group 设置为 primary。模块不校验 DataSource 类型，混用多个数据库时需要显式配置正确的工厂、数据源和会话组。

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

事务说明请同时读取 `20-transactions.md`；上面的服务使用 Spring `@Transactional`。级联查询、懒加载和懒同步见同目录 `12-relations.md`，其中区分了框架查询的自动增强与手动 DTO 的代理入口；它不是 SQL JOIN 的使用指南。尚未整理的高级多表 SQL 案例仍只作历史参考。

---

<!-- source: doc/topics/12-relations.md -->
## ORM 级联查询 懒加载和懒同步

框架自带查询结果增强：通过 ORM 查询得到的模型，会进入 `EntityProxyService`，由 `LazyProxyFactory` 处理关联查询；符合条件的关联对象再由 `SyncProxyFactory` 增加懒同步能力。通常不需要业务再次手动代理查询结果。自己 `new` 出来的聚合 DTO 则需要显式调用代理入口。

本专题说明 `@JoinQuery`、`@JoinLazy`、`@JoinSync`，以及用户、角色、菜单的关联示例。这是应用侧追加查询和代理处理，不是 SQL JOIN，也不是 JPA 的实体管理或任意对象自动持久化。

> 源码核对：2026-10-04 / JDK 25 / 制品版本 3.0。
> 来源：`core/.../session/AbstractSession.java`、`RouteSession.java`；`core/.../proxy/EntityProxyService.java`、`LazyProxyFactory.java`、`SyncProxyFactory.java`；`common/.../model/AbstractQueryModel.java`、`AbstractModel.java`。
> 代理行为已用真实 SQLite 集成测试验证；本文业务示例使用占位实体，仍需自行提供表结构和数据。事务边界同时读取 `20-transactions.md`。

### 查询后的自动处理

1. `RouteSession.select()` 将请求路由给实际 `EntitySession`。
2. `AbstractSession.select()` 查询数据库并将结果解码为模型，然后调用 `EntityProxyService.getLazyProxyList()`。分页和 `group()` 也有相应增强入口。缓存保存未增强模型，读取后复制为独立对象，再为当前查询创建代理，不复用上一次事务的代理。
3. `LazyProxyFactory` 遍历模型的 `@JoinQuery` 字段。没有 `@JoinLazy` 时立即加载关联，有 `@JoinLazy` 且路由事务开启时创建 getter 拦截代理。
4. 返回关联实体的字段有 `@JoinSync` 且路由事务开启时，结果再包装为同步代理；它不是给所有查询结果无条件套上两层代理。计数结果不属于同步实体。

| 注解组合 | 读取行为 | 修改行为 |
| --- | --- | --- |
| `@JoinQuery` | 增强阶段立即查询关联并赋值 | 普通对象，修改不因此自动写库 |
| `@JoinQuery` + `@JoinLazy` | 事务内访问未加载字段的 getter 时查询 | 仅延迟读取，不代表自动同步 |
| `@JoinQuery` + `@JoinSync` | 立即查询关联，有路由事务时创建同步代理 | 在事务内通过代理调用非 null setter，提交阶段写回 |
| 三个注解一起使用 | 事务内 getter 触发查询时创建关联同步代理 | 同上，必须真正加载并修改代理对象 |

业务使用 `ModelSelectWrapper.selectOne()/selectList()` 等查询入口时，模型中的关联字段会按上述链路处理。不要直接实例化内部工厂；`SyncProxyFactory` 本身是包内类，公共入口是路由或模型方法。

### JoinQuery 的取值规则

注解 import 为 `com.yulinlin.data.core.anno.JoinQuery`。

```java
// 放在一个已有映射模型的关联字段上。
@JoinField(exist = false)
@JoinQuery(primary = "id", value = "${sysDeptId}")
private SysDeptEntity department;
```

这里 `SysDeptEntity` 是业务实体，不是框架提供的类。查询目标模型的 `id`，使用当前对象的 `sysDeptId` 作为查询值。`primary` 是目标模型的匹配属性，默认 `id`，不要求它一定是数据库主键；例如也可以指定 `username`。

**动态取值必须写 `${...}`。** 当前 `LazyProxyFactory` 将没有 `${}` 的 `value` 当作固定字符串，不会自动将它理解为属性名：

| value 写法 | 当前含义 |
| --- | --- |
| `"${username}"` | 当前对象的 username |
| `"${user.sysRoleIds}"` | 当前对象 user 的 sysRoleIds |
| `"${roles.sysMenuIds}"` | 遍历 roles 集合，收集各角色的 sysMenuIds |
| `"username"` | 固定字符串 username，不是当前用户名 |

普通关联分支根据字段类型推断目标模型：`SysUserEntity` 对应单对象，`List<SysRoleEntity>` / `Set<SysRoleEntity>` 根据明确的泛型推断集合元素类型。不要使用原始 `List`、`List<?>` 或无法推断类型的字段。关联键为集合时会收集、去重后查询，源键与目标属性的 Java 类型需要一致。

普通列表关联汇集这一批父对象的键，去重后分批 `IN` 查询，再按目标属性建立索引分配，不逐个父对象遍历全部结果。单对象取匹配结果中的第一项，无匹配保持 null；集合赋空集合。索引支持一个键对应多条关联记录，单对象匹配条件应保证唯一。

`batchSize` 默认 512，限制每次 IN 的去重键数量，不是结果行数或事务提交大小。可写 `@JoinQuery(value = "${ids}", batchSize = 256)`。集合默认不保证输入 ID 顺序；指定 `order` 时各批执行数据库排序，跨批按属性的 Java 自然顺序合并。自定义 collation、TEXT 数字排序与 Java 比较规则可能不同，严格依赖这类排序时显式查询处理。

关系字段放在数据库实体中时显式标记 `@JoinField(exist = false)`，避免被当作数据库列。仅用于组装数据的 DTO 不需要 `@JoinTable`，但它引用的关联实体仍需完整映射和可用表。

### 用户角色菜单的级联示例

下面是一份完整 DTO。`demo.domain.SysUserEntity`、`SysRoleEntity`、`SysMenuEntity` 是业务侧模型占位，应替换为自己的类，不依赖 admin 模块接入业务。

示例的实体契约如下，相关属性都需要 getter，主键及需要同步的字段还需要 setter：

| 模型 | 必须提供的属性 |
| --- | --- |
| SysUserEntity | id、username、sysRoleIds；其中 sysRoleIds 为角色 ID 集合 |
| SysRoleEntity | id、sysMenuIds；其中 sysMenuIds 为菜单 ID 集合 |
| SysMenuEntity | id，以及需要查询的菜单字段 |

集合中的 ID 与被关联实体的 id 类型保持一致。缺少 `sysRoleIds` / `sysMenuIds`，或字段被注释，就不能仅靠 `@JoinQuery` 得到后续关联。本文使用 Lombok `@Data` 生成 getter/setter，需要启用注解处理；不使用 Lombok 时自行编写访问方法。

```java
package demo.dto;

import com.yulinlin.data.core.anno.JoinQuery;
import demo.domain.SysUserEntity;
import demo.domain.SysRoleEntity;
import demo.domain.SysMenuEntity;
import lombok.Data;
import java.util.List;

@Data
public class RouterDetails {
    private String username;
    private String loginType;

    @JoinQuery(primary = "username", value = "${username}")
    private SysUserEntity user;

    @JoinQuery(primary = "id", value = "${user.sysRoleIds}")
    private List<SysRoleEntity> roles;

    @JoinQuery(primary = "id", value = "${roles.sysMenuIds}")
    private List<SysMenuEntity> menus;

    public RouterDetails() { }

    public RouterDetails(String username, String loginType) {
        this.username = username;
        this.loginType = loginType;
    }
}
```

手动 DTO 的方法体用法：

```java
import com.yulinlin.data.core.session.SessionUtil;
import demo.dto.RouterDetails;

RouterDetails details = SessionUtil.route()
        .getLazyProxy(new RouterDetails(username, loginType));
// 此版本没有 @JoinLazy，入口执行时立即尝试加载 user、roles、menus。
```

`getLazyProxy()` 的名称不表示所有字段都延迟查询；是否延迟由 `@JoinLazy` 决定。仅声明注解后 `new RouterDetails(...)`，不会自行访问数据库。实现了框架 `AbstractQueryModel` 的模型也可调用 `createLazyProxy()`，普通 DTO 则用上面的路由入口。

即时加载按反射得到的字段顺序执行，没有关联依赖的拓扑排序。示例将 user、roles、menus 按依赖排列；不要将复杂关联图的正确性建立在反射顺序上，必要时显式分步查询。立即互相引用可能递归，当前路由有默认深度 6 的保护，不等于可以自动解决循环关系。

### JoinLazy 的使用

在上一节 DTO 中，将有关联依赖的三个字段一起改为延迟加载。下面是替换字段的片段，类上仍需有无参构造及普通 getter/setter：

```java
import com.yulinlin.data.core.anno.JoinLazy;
import com.yulinlin.data.core.anno.JoinQuery;

@JoinLazy
@JoinQuery(primary = "username", value = "${username}")
private SysUserEntity user;

@JoinLazy
@JoinQuery(primary = "id", value = "${user.sysRoleIds}")
private List<SysRoleEntity> roles;

@JoinLazy
@JoinQuery(primary = "id", value = "${roles.sysMenuIds}")
private List<SysMenuEntity> menus;
```

有路由事务时，懒代理先于立即关联加载创建，因此立即字段也可通过 getter 读取延迟依赖。全部懒字段按实际访问逐步加载，同批同字段共享状态；循环懒加载会抛出明确异常，仍应避免循环关系。

在同一线程的框架路由事务中创建、访问代理，方法体片段如下：

```java
import com.yulinlin.data.core.session.SessionUtil;

SessionUtil.route().transaction(() -> {
    RouterDetails details = SessionUtil.route()
            .getLazyProxy(new RouterDetails(username, loginType));
    var user = details.getUser();   // 此时才查询用户。
    var roles = details.getRoles(); // 使用已读取的用户角色 ID。
    var menus = details.getMenus(); // 汇集角色中的菜单 ID。
    // 在事务内用所需数据组装普通响应 DTO。
    return null;
});
```

也可将这段读取逻辑放在 Spring 管理的 Service public 方法内，使用 `@com.yulinlin.data.core.anno.JoinTransaction` 并经 Spring 代理调用。已兼容的 Spring `@Transactional` 路径见事务专题；不能仅因一个独立 JdbcSession 开着事务，就推定 `SessionUtil.route().isOpenTransaction()` 为 true。

使用时注意：

- 在未开启路由事务时，带 `@JoinLazy` 的字段被跳过，而且不会创建懒代理；它们不会自动退回立即加载。
- 未加载字段必须在创建代理的原始线程和原始路由事务内读取。事务外、其他线程或另一个新事务访问会抛出明确异常；已加载数据仍可在事务后读取。不要将未加载的代理直接交给 Controller 序列化或异步任务。
- 通过 getter 才能触发延迟查询。直接访问字段、反射读取字段或只持有原始对象不是等价入口；已经非 null 的字段也不会因为 getter 调用而重新加载。
- CGLIB 创建子类并使用无参构造，代理类不能是 final/record，相关 getter/setter 不能是 final 或不可代理方法。
- 同批同字段加载成功后，包括未匹配的 null 和空集合，不再重复查询。失败不会标记加载完成，可重试；显式 setter 赋值的关联不会被后续批量加载覆盖。

#### 列表批量懒加载

同一次 `selectList()` 的对象自动共享懒加载上下文。首次访问某个关联 getter 时，收集同批对象尚未加载的关联键，去重、分批查询并分配到所有对象。只加载被访问的字段，不展开全部关联图。

手动 DTO 列表应一次传入，下面使用上文采用 `@JoinLazy` 的 RouterDetails：

```java
SessionUtil.route().transaction(() -> {
    return SessionUtil.callable("oss", () -> {
        List<RouterDetails> details = SessionUtil.route().getLazyProxy(
                List.of(new RouterDetails("alice", "password"),
                        new RouterDetails("bob", "password")));
        details.get(0).getUser(); // 收集 alice/bob，查询并分配这一批 user。
        details.get(1).getUser(); // 不再为 bob 单独查询。
        return null;
    });
});
```

不要循环逐个 `getLazyProxy(dto)`，否则每个对象属于独立批次。不同查询的列表不自动合并。`wheres` / `model` 的复杂条件和计数仍逐父对象执行，不承诺普通 IN 的批量合并。

### JoinSync 的使用

懒同步指延后数据库更新，不是后台线程同步。它捕获事务内的 setter 调用，正常提交阶段再使用现有更新 Wrapper 写入数据库。

#### 自动增强关联对象

在上一节的 user 字段上增加 `@JoinSync`。它可以与 `@JoinLazy` 组合，下面是字段片段：

```java
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinSync;

@JoinSync
@JoinQuery(primary = "username", value = "${username}")
private SysUserEntity user;
```

随后在路由事务内读取、修改关联代理。这里假设业务用户实体有可更新的 `nickname` 属性：

```java
SessionUtil.route().transaction(() -> {
    RouterDetails details = SessionUtil.route()
            .getLazyProxy(new RouterDetails(username, loginType));
    SysUserEntity user = details.getUser();
    if (user == null) throw new IllegalStateException("user not found");
    user.setNickname(newNickname);
    // 不需要再手动执行 createUpdateWrapper().execute()。
    return null;
});
// 正常提交阶段尝试写回；异常则回滚并清理待同步记录。
```

关联目标必须是可更新的映射实体，具备 `@JoinTable` 和非 null 的 `@JoinMeta(primaryKey = true)` 主键；推荐沿用 ORM 专题中的 IdEntity。不要把同步代理用在没有可靠更新定位条件的投影 DTO 上，也不要在待同步对象上改主键。

#### 显式增强普通实体

普通查询结果不代表根实体已经具备懒同步。没有相应关联字段增强时，可显式创建同步代理；下面使用 ORM 专题中的 `demo.domain.DemoUser`，方法体片段为：

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.session.SessionUtil;
import demo.domain.DemoUser;

if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
SessionUtil.route().transaction(() -> {
    // 查询、创建代理时保持同一个明确的会话上下文。
    return SessionUtil.callable("mysql", () -> {
        DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                .eq("id", id).selectOne();
        if (user == null) throw new IllegalStateException("user not found");
        DemoUser syncUser = SessionUtil.route().getSyncProxy(user);
        syncUser.setStatus(1);
        return null;
    });
});
```

模型也有 `createSyncProxy()` / `commitUpdate()` 快捷入口。`createSyncProxy()` 在未开启路由事务时会自行开启一个；`commitUpdate()` 实际结束路由的一层事务，不只是提交当前对象。需要自己配对提交/异常回滚，已有业务事务内优先沿用路由回调，不随意提前调用 `commitUpdate()`。

`@JoinSync` 的声明允许 TYPE 和 FIELD，但查询处理检查关联字段上的注解；不要仅在任意根实体类上加注解，就假定所有查询结果会自动变成同步代理。显式 `getSyncProxy()` 不要求类上有这个注解。

#### 提交顺序和跟踪边界

`EntityProxyService` 是事务监听器。框架事务最外层提交时，setter 记录按原始数据源、实体类型分组，构造部分更新，再结束实际 Session 事务；内层结束不清理跟踪。最终 `afterCompletion()` 释放上下文，回滚丢弃记录，但不恢复 Java 对象。

存在原生 Spring 事务时，同步记录注册 `beforeCommit` 钩子，在 Spring 物理提交前更新，避免框架外层切面返回时才写入已结束的连接。只读事务禁止同步 setter。已验证普通注解和 TransactionTemplate，不代表任意传播方式或分布式原子事务。

这个机制不是完整的脏检查：

- 只记录真实持久化属性的单参数 setter，业务 setter 执行一次并保存执行后的值；`setUp()` 等普通方法不会误记录。`setNickname(null)` 不清空数据库列，还会取消该字段此前的待同步值，保持 null 跳过语义。
- `getIds().add(...)`、修改嵌套 Map/对象、直接写字段或修改未代理的原对象，不会自动产生 setter 记录。
- 没有被跟踪的 setter 调用就不加入待更新列表；同值 setter 也可能被计为修改，不会先比较数据库或旧值。
- 不构造空更新实体。SQL 使用原始主键定位，仅写 setter 记录及 `updateBefore()` 本次生成的字段；未修改的 0、false、非 null 初始化值不会自动更新。字段映射、update=false 和 inc/dec 策略继续生效。
- 创建时要求完整非 null 主键，禁止通过代理改主键，提交时再次校验；支持多主键组成定位条件。缓存按数据源和对象身份隔离，不按实体 equals/hashCode 合并。
- 懒同步版本字段支持 `@JoinField(version = true)` 的 Integer/int、Long/long：使用原始版本条件并递增，更新条数不匹配报乐观锁失败；版本由框架维护。
- 代理绑定原始线程和路由事务，事务结束后或另一个事务里调用同步 setter 会报错。自动更新清理对应实体缓存；查询缓存保存未增强数据，读取时复制为独立对象，避免回滚对象污染缓存。这增加了缓存读取的复制成本，但不缓存旧事务代理。

缓存查询的模型还需满足 `ReflectionUtil.deepClone` 的支持边界，包括可用无参构造及可写属性。含不可复制的资源对象（如打开的流）时不要启用 `.cache()`；复制失败会显式报错，不退回共享旧对象。

集合、Map 和嵌套对象内部变化明确不跟踪，需要同步时调用对应持久化属性 setter，或显式更新。setter 捕获对象引用而非深快照，setter 后修改同一引用可能影响最终编码值；不检测内部变化不代表冻结对象。

### 关联会话和高级参数

关联字段可以指定会话，例如本地 SQLite：

```java
import com.yulinlin.data.core.anno.JoinSession;

@JoinSession("sqlite")
@JoinQuery(primary = "id", value = "${localUserId}")
private LocalUserEntity localUser;
```

`LocalUserEntity` 与 `localUserId` 由业务提供。未在字段指定会话时，懒上下文记住创建时的数据源组；getter 即使发生在其他会话栈里，也使用原组。字段 `@JoinSession` 覆盖关联查询的数据源和 cluster；后续同步更新路由至该组的写会话。手动 DTO/同步代理创建时，用 `SessionUtil.callable("oss", ...)` 明确来源组。

注解还有以下参数，当前实现并非每个分支行为完全相同：

| 参数 | 当前实现 |
| --- | --- |
| `order = @JoinOrder(name = "sortValue", asc = true)` | 对关联查询排序；默认 asc 为 false |
| `batchSize` | 默认 512，普通关联每次 IN 的去重键上限，不控制事务提交或总结果数 |
| `wheres = {...}` | 按每个父对象分别构造条件并查询，可能产生 N+1；不等同于普通 IN 合并查询 |
| `size` | 正值仅在 wheres/model 分支设置分页；普通 primary/value 分支不应用该限制 |
| `model = SomeEntity.class` | 当前进入 count 分支，不是给对象或 List 覆盖泛型的通用选项；不要套用其他 ORM 的理解 |

`wheres` 返回关联实体时可结合 `@JoinSync`；`model` 的 count 结果不是同步实体。复杂条件仍逐父对象执行，空条件可能跳过，不是普通 IN 的同义写法。

### 交付前检查

确认 `${...}` 语法、源属性存在、关联泛型与 ID 类型、目标主键唯一性、实体映射、无参构造和可代理方法。懒加载与懒同步必须检查框架路由事务、执行线程及关联会话；对外返回前在事务内读取所需数据并组装普通 DTO，不把整个关联图默认当作接口响应。

验证入口为 `sqlite/.../OrmProxyIntegrationTest.java`：真实临时 SQLite 文件覆盖列表/单对象、分批 IN、空结果、依赖链、循环及重试、来源数据源、setter/默认值/null、嵌套事务、缓存隔离、主键/版本，以及 Spring 提交回滚。业务占位模型不属于这些测试，不代表全部文档示例已执行。未运行外部 MySQL 服务或性能基准。

---

<!-- source: doc/topics/15-datasources.md -->
## 多数据源：创建、注册与选择 JDBC 会话

> 状态：2026-10-05 按数据库模块自行注册 Session 和负载均衡默认组更新。本轮未运行测试、编译或打包，未连接真实数据库运行多数据源集成测试。
> 适用：JDK 25、制品版本 3.0、Spring Boot 3.5；本例两个数据源均为 MySQL。
> 源码：`JdbcSessionFactory`、`YulinlinCoreAutoConfig.routeSession`、`DataJdbcApplication`、`MysqlParseAutoConfig`、`RegisterSession`、`JoinSessionAop`、`RouteSession`。

### 1. create 与注册不是同一步

```java
JdbcSession create(DataSource dataSource, String group)
JdbcSession create(DataSourceProperties properties, String group)
```

`group` 是路由使用的会话组名，不是数据库名，也不是 Spring Bean 名。工厂会设置解析器、编码器、缓存、过滤器等依赖并返回会话，但 **create 本身不注册路由**。

Spring 推荐路径：将返回对象声明成 `@Bean`。core 自动配置注入 `List<EntitySession>`，然后执行 `routeSession.registerSession(list)`。因此通常无需自己再 registerSession。

| Spring DataSource Bean | Spring 会话 Bean | 会话组 | 生成方式 |
| --- | --- | --- | --- |
| `dataSource`（@Primary） | `mysqlSession` | `mysql` | MySQL 模块自动配置用自己的工厂创建 |
| `ossDataSource` | `ossSession` | `oss` | 自定义 Bean 调用 factory.create |

`@Primary` 解决 Spring DataSource 注入歧义；group 是框架路由名，二者不同。模块默认组为 mysql、postgresql、sqlite。未指定 group 且只有一个注册组时自动使用该组；多个组时使用 yulinlin.datasource.default-group 或 setDefaultGroup 指定的组，未设置或组不存在时明确报错，不按注册顺序猜测。primary 也是普通组名，没有隐藏的优先级。

#### 全局默认会话组

下面设置应用共享负载均衡器的默认组；mysql 必须是已注册的会话组，不是 DataSource Bean 名：

```yaml
yulinlin:
  datasource:
    default-group: mysql
```

也可通过代码设置。以下是业务方法体片段，loadBalance 为容器注入的 `com.yulinlin.data.core.loadbalan.LoadBalance` 单例：

```java
loadBalance.setDefaultGroup("mysql");
String configured = loadBalance.getDefaultGroup(); // 配置值
String effective = loadBalance.defaultGroup();    // 实际默认组，单组时直接使用唯一组
```

配置只影响未显式选组的请求，Model Wrapper 的 group 参数、模型 @JoinSession 和当前 Service 会话上下文仍优先。只有一个组时不要求 default-group，即使配置值与唯一组不同也直接使用唯一组；存在多个节点的同组仍按集群标签和权重选节点，不直接拿第一个节点。

框架默认 LoadBalance Bean 会绑定此配置。若用户自己声明 LoadBalance Bean，应自行注入 LoadBalanceProperties 或调用 setDefaultGroup；自定义实现需要支持默认组接口。运行时修改会影响后续未限定请求，应在没有在途业务事务的配置窗口进行。

注册和移除发布只读快照，读路径不加注册锁。权重必须非负，0 表示不参与选择；权重总和使用 long，随机区间为 [0, total)，避免第一节点偏置。单个可用节点不执行随机选择，但不会绕过主从标签或健康过滤。RandomLoadBalance.ping() 更新健康快照；现有 heartbeat 不启动后台定时任务，不能把这项优化描述成新增自动巡检。

非事务路由不再永久缓存已选节点，后续请求能看到权重或健康变化；事务内仍按组和标签保持节点固定。快照保护集合访问，并不等于动态卸载、关闭底层资源对在途请求完全安全；运行中的 group/cluster 不宜随意修改。

### 2. 完整配置：保留自动 mysql，再新增 oss

前提：已引入 starter + mysql，配置类在应用组件扫描范围内。此例替代原单数据源手工配置；不要再保留另一份同名 DataSource Bean。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/main_db
    username: ${MAIN_DB_USERNAME}
    password: ${MAIN_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
oss:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/oss_db
    username: ${OSS_DB_USERNAME}
    password: ${OSS_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MultiDataSourceConfig {
    @Bean("mainDataSourceProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainProperties() {
        return new DataSourceProperties();
    }

    @Bean("dataSource")
    @Primary
    public DataSource mainDataSource(
            @Qualifier("mainDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossDataSourceProperties")
    @ConfigurationProperties("oss.datasource")
    public DataSourceProperties ossProperties() {
        return new DataSourceProperties();
    }

    @Bean("ossDataSource")
    public DataSource ossDataSource(
            @Qualifier("ossDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossSession")
    public JdbcSession ossSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("ossDataSource") DataSource dataSource) {
        return factory.create(dataSource, "oss");
    }
}
```

为什么主数据源也显式定义：应用新增 DataSource Bean 会影响 Boot 默认数据源的条件装配；不要只定义第二个数据源，却假定主数据源一定仍会自动创建。本例明确提供两个数据源，并用 @Primary 指定 MySQL 模块的 `mysqlSession` 应使用哪一个。

本例已有组 mysql 的自动会话，不要再额外声明另一个同组会话。各模块直接调用自己的工厂，不检查 JDBC URL，也不由公共 JDBC 层选择工厂。两个不同会话对象使用同一组名会作为同组节点注册，不是按组名覆盖。手动覆盖某模块会话时使用对应默认 Bean 名；声明 jdbcSession 则会让 MySQL 和 PostgreSQL 的默认创建都退让。

同时引入 MySQL 与 PostgreSQL 时，默认组名虽然不同，但两边仍可能注入同一个 @Primary DataSource。group 名不会自动选中对应的 DataSource Bean，也不会修正错配的驱动或 SQL。应显式声明由正确工厂和数据源创建的会话；有 @Primary 主库时可将主会话命名为 jdbcSession 禁用双方默认创建，再为其他库注册独立组。完整例子见 PostgreSQL 专题。

与 SQLite 或 PostgreSQL 共存时，工厂必须用 `@Qualifier("mysqlSessionFactory")` 指定；PostgreSQL 使用 `@Qualifier("postgresqlSessionFactory")`。直接注入会话时也应使用实际生效的 Bean 名，例如 `@Qualifier("mysqlSession")`，避免同时引入多个模块或存在多个会话时仅按 JdbcSession 父类型注入产生歧义。手动 create 由调用方正确选择工厂。

工厂应使用 Spring 注入的实例，不能直接 `new JdbcSessionFactory(...)` 后就调用 create，因为其内部依赖需要注入。SQL 差异由对应 ParseManager 注册的解析器处理，JDBC 参数绑定和结果读取差异由 Session 的扩展方法处理；PostgreSQL 工厂创建 PostgresqlSession 并设置 PostgresqlParseManager，MySQL 工厂创建公共 JdbcSession 并设置 MysqlParseManager。不能仅把 MySQL 工厂的数据源地址改为 PostgreSQL/Oracle 就承诺兼容。

### 3. 如何指定使用 oss

#### 方式 A：业务方法上的 @JoinSession

```java
package demo.service;

import com.yulinlin.data.core.anno.JoinSession;
import demo.domain.DemoUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class OssUserService {
    @JoinSession("oss")
    @Transactional(rollbackFor = Exception.class)
    public List<DemoUser> findEnabled() {
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getStatus, 1).selectList();
    }
}
```

DemoUser 定义在 ORM 专题中，本例假设 oss_db 也有该实体对应的表。调用必须经过 Spring 代理；不能靠同类自调用切换数据源。注解也可放在 Service 类上，方法注解优先于 Service 类注解。

#### 方式 B：实体固定会话组

在已有实体类上添加 `@com.yulinlin.data.core.anno.JoinSession("oss")`。RouteSession 在请求未显式指定组时读取模型注解；这不是 DataSource Bean 的 qualifier。

#### 方式 C：模型包装器显式指定

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import demo.domain.DemoUser;

// 放在业务方法中；事务边界按下节说明设置
var query = ModelSelectWrapper.newInstance("oss", new DemoUser());
var users = query.selectList();
```

主路径组选择优先级：请求显式组 → 模型 @JoinSession → Service 切面压入的当前组 → 负载均衡器默认组选取（单组自动使用；多组使用 default-group，或要求显式指定）。不要在模型固定 oss 后，假定 Service 上的另一个组一定覆盖它。

### 4. 事务与生命周期限制

- Spring `@Transactional` 已接入框架切面，但 `@JoinSession` 不是 Spring 事务管理器选择器，不会自动创建或切换 PlatformTransactionManager。
- JdbcSession 自己管理事务和连接，不再根据已注册的组数切换连接模式。没有外层事务时，每次 ORM 请求自动完成自己的事务；Session 也能直接执行已准备的 ExecuteRequest/QueryRequest，不依赖 RouteSession。
- RouteSession 在事务中按需加入会话，只结束自己加入的那一层事务；独立 Session 已有的外层事务仍由调用方结束。业务需要合并多个请求时，使用框架事务注解或 route.transaction。
- 检测到同一个 DataSource 已由 Spring 绑定事务连接时，保持调用线程上的单连接执行，由 Spring 完成物理提交/回滚，不再另开并发连接绕过 Spring。
- 跨库提交不是 XA/分布式原子事务；Propagation、隔离、noRollbackFor、异步等语义不能仅凭同名注解推定完全一致。详细边界见事务专题。
- DataSource 单独声明成 Bean 有利于容器管理连接池生命周期。`create(DataSourceProperties, group)` 内部会新建 DataSource；不要默认它等价于单独的、由容器销毁的 DataSource Bean。

### 5. 手动注册与验收

非 Bean 动态会话可以在路由初始化完成后调用：

```java
// factory、route 和 dataSource 均为已配置好的对象
JdbcSession session = factory.create(dataSource, "reporting");
route.registerSession(session);
```

这里 route 类型为 `com.yulinlin.data.core.session.RouteSession`。不要在创建会话 Bean 的方法中反向注入 RouteSession 来注册，可能形成初始化循环。已经作为 EntitySession Bean 自动注册的会话无需再次注册。

当前注册表没有提供完整的并发动态管理保证；不要将这个片段当作运行时随意增加/移除租户库的生产方案。

启动后检查 `SessionUtil.route().loadBalanceList()` 是否包含 mysql、oss。主库查询显式选择 mysql，例如 `ModelSelectWrapper.newInstance("mysql", DemoUser.class).selectList()`。两个库预置不同标记数据，通过代理调用上述 Service 确认选库；分别验证正常提交、异常回滚和跨库失败行为。此文档没有替你执行这些数据库操作。

### 6. 大集合：最多 4 个连接，每次 JDBC batch 默认 256 条

普通集合 execute 本身已经使用 JDBC batch；增加 `.batch()` 才申请多连接并发写入，不需要修改原业务 API。先检查实际 Session 的 `supportsParallelWrites()`，不支持时**不做并发分组**，在调用线程用一个连接处理整批数据。

支持时，将全部解析结果均匀分成最多 x 个大组，每组提交一个任务，在整个任务内持续使用一个连接。同一 SQL 模板复用一个 PreparedStatement，每满 256 条执行一次 `executeBatch()`，最后执行不足 256 条的尾批。比如 10 万条、4 个连接：最多 4 个任务，每组 2.5 万条，而不是提交数百个 128 条任务。

`ExecuteRequest.batchSize` 仍是启用异步执行的**整次请求最小条数**，默认 128；不等于 JDBC batch 大小。整次请求不足阈值时合为一组同步执行；达到阈值后，即使某个工作组不足 128 条也可并发。未调用 `.batch()` 或未提供执行器时不并发分组。解析仍会持有整个输入集合，不是流式导入。

```java
ModelInsertWrapper.newInstance("oss", usersToInsert).batch().execute();
// 更新、删除也保留现有的 .batch() 入口。
```

默认值可省略；用户在应用外围配置：

```yaml
yulinlin:
  datasource:
    jdbc:
      parallel-connections: 4
      execute-batch-size: 256
```

两项均要求正整数，独立控制并发连接数和一次 `executeBatch()` 的行数。`parallel-connections: 1` 表示不并发分组。连接数是**每个 Session、每个框架事务的物理连接上限**，不是整个应用的并发上限，也不是连接池的 maximumPoolSize。连接按需创建，不会每次请求固定打开 4 个；HikariDataSource 下还会限制为池的 maximumPoolSize，实际并行度也受数据量和执行器线程数限制。

可以按会话覆盖，在会话未开启事务时调用：

```java
JdbcSession session = factory.create(dataSource, "reporting");
session.setParallelConnections(2);
session.setExecuteBatchSize(512);
boolean parallelAllowed = session.supportsParallelWrites();
// Spring Bean 或手动注册均沿用前文方式。
```

`supportsParallelWrites()` 是与当前线程事务上下文有关的能力查询，不会为了查询而打开连接；它不表示连接池当前还有多少空闲连接，也不保证数据库性能收益。SQLite 内置会话固定使用 1 个连接，返回 false，不受通用默认值 4 影响；Spring 已绑定事务连接或 Hikari 最大连接数为 1 时也返回 false。上层统一通过这个函数决定是否分组，不另建 SQLite Session 子类。要用多连接批处理，用框架 `@JoinTransaction`/route.transaction，避免同时用 Spring 事务管理器绑定同一数据源。

**执行 JDBC batch 不等于提交事务。**每 256 条只发送一批参数，所有组执行完成后才沿用 Session/RouteSession 的事务提交边界；后续批次失败，单连接事务中之前执行的批次一起回滚。驱动返回 `SUCCESS_NO_INFO` 时按一条成功命令计数，不代表精确受影响行数；`EXECUTE_FAILED` 或 BatchUpdateException 都使请求失败。

批次提交或同步执行中途失败时，框架仍等待**所有已提交任务**结束，再处理回滚和释放连接；不会因为最后一批是同步任务而提前返回。内层回滚或执行失败被业务捕获，也会标记 rollback-only，不能继续把外层 JDBC 事务正常提交。

多连接拥有各自的数据库事务，逐个提交，不是一个原子数据库事务：提交中途失败时，已经提交的连接无法撤销；提交前跨连接查询也不保证读到其他连接的未提交写入。需要单库严格原子性和事务内读己之写，配置 `parallel-connections: 1`。外围连接池需给其他请求预留容量；多个批量事务同时占用连接仍可能等待或超时，不应把并发值等同于无成本加速。未连接真实 MySQL 做吞吐量测试，不承诺 4 倍性能。

---

<!-- source: doc/topics/16-sqlite.md -->
## SQLite：本地文件数据库

> 适用：JDK 25 / 制品版本 3.0。实现位于 `sqlite/`；通用 SQL 解析位于 `jdbc/.../sql/`。
> SQLite 与 MySQL 复用普通 CRUD 解析器和现有 Model Wrapper API，不需要另一套实体或 DAO。
> 2026-10-05 更新默认文件和 sqlite 会话组；引入模块即启用。本轮未运行测试、编译或打包。

### 最小接入

Spring Boot 项目添加依赖；已有 starter 时不用重复添加。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>sqlite</artifactId>
    <version>3.0</version>
</dependency>
```

仅使用 SQLite 时不需要 mysql 模块、数据库服务器、用户名或密码。引入模块即启用，无需提供 yulinlin.sqlite 配置：文件为进程工作目录下的 data/local.db，会话组为 sqlite。下面是可选的覆盖配置，未设置的字段沿用默认值：

```yaml
yulinlin:
  sqlite:
    file: data/local.db
```

相对路径基于进程工作目录，不是 classpath。启动时创建父目录与数据库文件，并启用 WAL；默认不创建业务表，开启下文实体扫描后可以自动建表。生产环境建议使用持久化目录的绝对路径。未配置 `file` 时使用 data/local.db，不再以 file 是否存在决定启用；显式配置空路径仍会校验失败。只接受文件路径，不接受 JDBC URL、内存数据库或 `file:` URI。

默认会话组是 `sqlite`，请求时使用 `newInstance("sqlite", ...)`。只有 sqlite 一个会话组时可以省略 group；多个组并存时显式选择，或设置 yulinlin.datasource.default-group=sqlite；旧 primary 组也需显式配置为默认，不能仅凭组名自动优先。旧业务需要 local 组时可显式设置 yulinlin.sqlite.group=local。无需为了省略参数把 SQLite 组改成 primary。实体映射和 CRUD 按 ORM 专题使用；将 MySQL 建表语句换成 SQLite DDL，不要照搬 `ENGINE`、`AUTO_INCREMENT` 等 MySQL 专用语法。

### CRUD 完全沿用现有用法

#### 可选：扫描实体自动建表

```yaml
yulinlin:
  sqlite:
    file: data/local.db
    schema:
      enabled: true
      packages:
        - com.example.local.entity
```

自动扫描指定包及子包中带 `@JoinTable("表名")` 的具体实体，在 SQLite 会话创建前完成建表。不实例化实体，不注册成 Spring Bean；仅操作模块内置的 SQLite 连接池，不操作 MySQL。默认关闭；开启却未指定包、没有扫描到物理表实体时启动失败，避免配置错误被忽略。

复用现有继承字段、`@JoinField(name=...)`、`@JoinMeta(primaryKey=true)` 和 JDBC 驼峰列名配置。跳过静态/transient 字段、`exist=false`、计算字段以及 JOIN 查询模型。建议扫描专用实体包，不要混入查询投影 DTO。表名必须是普通名称，不支持别名、SQL 表达式或限定名称；不自动创建索引、外键或复合主键。主键值继续按原框架方式提供，不新增生成主键回填机制。

| 实体字段类型 | 自动建表列类型 |
| --- | --- |
| byte/short/int/long 及包装类、boolean/Boolean | `INTEGER` |
| float/double 及包装类 | `REAL` |
| 日期时间、枚举、BigDecimal、BigInteger | `TEXT` |
| String、字符、JSON 对象、Map、集合 | `TEXT` |
| byte[] | `TEXT`，现有 ByteArrayCoder 输出 Base64 |

编解码继续使用现有 SQL 编解码器，不新增转换层。自定义编码器必须与上述存储约定兼容；建表不保证任何任意 Java 类型都可以被现有编码器正确读写。

`BigDecimal` 存 TEXT 可保留现有字符串编码精度，但数据库直接排序、范围比较是文本语义，**不等于数值大小比较**。日期统一 `yyyy-MM-dd HH:mm:ss` 且时区一致时可做文本范围查询；当前 DateCoder 只有秒精度。

也可以注入管理器手动执行（方法体片段）：

```java
// 注入 com.yulinlin.jdbc.sqlite.SqliteSchemaManager schemaManager
var result = schemaManager.scanAndCreate("com.example.local.entity");
// 或只处理明确指定的实体：
schemaManager.createTables(SysUserVo.class, LocalConfig.class);
// result.entities() / result.created() / result.existing()
```

手动入口即使自动扫描关闭也可使用；应在业务事务外、没有并发迁移时调用。每批 DDL 使用一个事务，失败全部回滚。不存在的表创建，已有表检查列类型亲和性和主键；缺列、类型不兼容或同表映射冲突会报错，不自动补列、删表或改数据。额外普通列允许保留，但额外列自身的 NOT NULL、CHECK、触发器等约束仍由数据库执行，本工具不是完整迁移/约束验证器。

以下是方法体片段，假设 `SysUserVo` 是现有框架实体、对应业务表已创建：

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;

// SQLite 默认会话组为 sqlite。
var users = ModelSelectWrapper.newInstance("sqlite", SysUserVo.class).selectList();

// 批量插入：一次传入集合，内部使用事务和 JDBC batch。
ModelInsertWrapper.newInstance("sqlite", usersToInsert).execute();
```

条件、排序、分页、更新和删除继续使用原 Wrapper，不要自行拼接用户输入。批量插入要使用待插入的新数据，不要将查询结果直接重复插入。框架不自动阻止无条件更新或删除。

### 与 MySQL 同时使用

保留 mysql 模块与原 `spring.datasource`，SQLite 默认使用独立的 `sqlite` 组，以下 `group: sqlite` 可省略：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/app
    username: app
    password: ${DB_PASSWORD}
yulinlin:
  sqlite:
    file: data/local.db
    group: sqlite
```

```java
// 第一个参数就是数据源会话组，与 oss 的选择方式相同。
var localUsers = ModelSelectWrapper.newInstance("sqlite", SysUserVo.class).selectList();
var mysqlUsers = ModelSelectWrapper.newInstance("mysql", SysUserVo.class).selectList();
```

`spring.datasource.hikari` 仍用于主库。SQLite 使用模块内置的连接池，不继承 MySQL URL 或连接池设置，也不注册 `DataSource` Bean。连接池由 `SqliteDatabase` 创建并在应用关闭时释放；业务无需声明或注入 SQLite DataSource。自定义主 DataSource Bean 时，多主库按 Spring 的规则选择 `@Primary`；SQLite 组保持 `sqlite` 即可，不会参与 DataSource Bean 的选择。不要将不同数据库注册在同一个组下做随机路由。

### 默认值与性能取舍

| 配置/行为 | 默认值 | 含义 |
| --- | --- | --- |
| `file` | `data/local.db` | 本地文件路径，相对路径基于进程工作目录 |
| `group` | `sqlite` | Wrapper 第一个参数指定的会话组 |
| `busy-timeout` | `5000` | 等待 SQLite 锁的毫秒数，不是查询超时 |
| `synchronous` | `NORMAL` | 可选 `FULL`；不提供关闭同步的默认方案 |
| 日志模式 | WAL | 启动时启用并验证，失败则启动失败 |
| 外键 | ON | 每个物理连接启用外键检查 |
| 连接池 | 1 个连接 | 本模块池内操作串行，减少写连接争抢 |

```yaml
yulinlin:
  sqlite:
    file: data/local.db
    busy-timeout: 5000
    synchronous: FULL
```

WAL 不代表多个写事务可以同时执行。单连接方案偏向简单可靠的本地写入，不是最大化读并发；长事务会占用唯一连接。连接池等待上限为 `max(1000, busy-timeout + 1000)` 毫秒，超时抛异常，不会无限等待。

`NORMAL` 是性能与持久性的折中，断电或操作系统崩溃时最近已提交的数据可能丢失；更重视持久性使用 `FULL`。批量写入优先一次提交集合，不要每行单独开事务。没有针对你的业务负载跑性能基准，因此不承诺吞吐量。

### 事务

不写事务注解也能执行 CRUD；这不等于禁用 SQLite 事务。单次 ORM 请求内部提交，批量失败回滚，多个独立调用不会自动合并成一个业务事务。

SQLite 直接参与框架的会话事务，不自动注册 `sqliteTransactionManager`，也无需业务注册。框架会记录事务中使用的会话，在正常结束时逐个提交、异常时逐个回滚。

```java
// 放在 Spring 管理的 Service public 方法上，通过代理调用。
@com.yulinlin.data.core.anno.JoinTransaction
public void saveLocal() {
    ModelInsertWrapper.newInstance("sqlite", usersToInsert).execute();
    // 同一事务内继续执行其他框架 ORM 操作
}
```

也可以使用框架已兼容的 Spring `@Transactional` 注解，或直接调用 `SessionUtil.route().transaction(() -> { ... })`。仅有 SQLite 时，不需要为此额外创建 Spring JDBC 事务管理器，注解由框架事务切面管理会话。与 MySQL 共存时，Spring 自身的事务拦截器可能仍管理主库；跨框架会话的事务边界推荐使用 `@JoinTransaction`。

SQLite 直接使用通用 `JdbcSession` 和 `JdbcSessionFactory`，不另建 SqliteSession 子类；只配置 SQLite 解析器和单连接上限。各 JdbcSession 都保持自己的连接与事务状态，SQLite 的 `.batch()` 仍在同一连接同步执行。多数据源事务逐个提交，并非分布式原子提交；中途提交失败不能保证其他已经提交的数据回滚。`REQUIRES_NEW`、保存点、`noRollbackFor` 等 Spring 高级事务属性不属于框架切面的完整语义。

SQLite 的 `session.supportsParallelWrites()` 返回 false：整批请求不做并发分组，不投递写入工作线程，减少 SQLite 单一写入者限制下的锁竞争。底层仍默认每 256 条执行一次 JDBC batch，并复用同一个连接和同一 SQL 模板的 PreparedStatement；这不是每 256 条 commit。可通过 `yulinlin.datasource.jdbc.execute-batch-size` 调整批次大小，不改变事务边界；`parallel-connections` 的通用配置不覆盖 SQLite 的单连接限制。

### 方言与运维边界

- 普通 CRUD、条件、排序和分页复用 JDBC 通用解析；MySQL 原解析器类名保留兼容入口。
- SQLite 不支持 `SELECT FOR UPDATE`，底层 `SelectWrapper.lock()` 解析时明确报错，不静默忽略锁；这不是 ModelSelectWrapper 的方法。
- MySQL 的日期/时间间隔分组不直接复用；SQLite 当前对此明确报不支持。自定义 SQL、函数、JSON 和复杂 JOIN 的语义需要按 SQLite 验证，不保证所有 MySQL SQL 等价。
- WAL 文件需要可写的本地目录，不要把数据库放在网络共享盘。运行期间不要单独删除 `-wal`、`-shm` 或只复制主文件作为可靠备份。
- JDK 25 的 SQLite 原生库可能提示 native-access 警告，启动时可添加 `--enable-native-access=ALL-UNNAMED`。

### 验证范围

集成测试位置：`sqlite/src/test/java/com/yulinlin/jdbc/sqlite/SqliteIntegrationTest.java`。覆盖真实临时文件、WAL/同步/外键设置、CRUD、分页、批量失败回滚、Spring 注解的框架回滚、多会话提交与回滚、重开文件，以及 MySQL/SQLite 独立会话路由。共存测试不连接外部 MySQL，不等于 MySQL 服务器回归测试。

```shell
mvn -pl sqlite -am -Dtest=JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

<!-- source: doc/topics/17-postgresql.md -->
## PostgreSQL 接入与方言范围

> 状态：2026-10-05 按解析器注册机制和数据库模块自行创建 Session 更新。本轮未运行测试、编译或打包；此前回归结果不代表本轮验证。真实 PostgreSQL 测试需显式提供测试库，默认跳过。没有 PostgreSQL 性能实测。
> 适用：JDK 25、制品版本 3.0、Spring Boot 3.5。
> 源码：PostgresqlAutoConfiguration、PostgresqlSession、PostgresqlParseManager、SqlParseManager、SqlParamsContext、DataJdbcApplication、JdbcSessionFactory。

PostgreSQL 使用 PostgresqlSession，继承公共 JdbcSession 的连接、事务、批处理与查询执行。Session 设置 PostgresqlParseManager，由它注册 PostgreSQL 专属解析器生成 SQL；Session 只额外处理驱动参数绑定与结果读取，不承担 SQL 拼接，也不使用独立 SqlDialect。postgresql 模块可独立使用，不依赖 mysql 模块或 MySQL 驱动，测试也不引入 mysql；不能把 mysqlSessionFactory 用于 PostgreSQL。

### 1 依赖与单数据源配置

最小依赖只需 postgresql，公共 jdbc、core 和 lang 会传递引入；不需要 mysql 或 starter。通过团队制品仓库或本地发布获取，不假设已经发布到 Maven Central。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>postgresql</artifactId>
    <version>3.0</version>
</dependency>
```

正常 Spring Boot 自动配置链下无需额外启用注解。已有表与可用的数据库账号是前提，本模块不扫描建表。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/demo?stringtype=unspecified
    username: ${PG_USERNAME}
    password: ${PG_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
```

日期、枚举、BigDecimal、Map、集合和嵌套 Bean 等仍经过现有 JDBC 编码器，部分值编码成字符串。示例设置 `stringtype=unspecified`，让 PostgreSQL 根据目标列或 SQL 上下文推断参数类型，避免将 JSON 字符串直接绑定为 varchar 后写入 jsonb 等类型时报错。它是驱动配置，不是框架自动添加的参数；每个 PostgreSQL 数据源都要分别设置。[pgJDBC 参数说明](https://jdbc.postgresql.org/documentation/use/)

该参数不能解决所有类型推断问题。无明确类型上下文的自定义 SQL 应写显式 CAST，例如 `CAST(#{value} AS jsonb)`；不应假定 UUID、数组、空间类型、原生 enum 等 PostgreSQL 扩展类型已经都有专用编码器。

PostgresqlAutoConfiguration 在模块内部注册 `postgresqlSessionFactory`，并直接用自己的工厂创建 Bean `postgresqlSession`，实际类型为 PostgresqlSession，组名为 `postgresql`；core 自动收集实际的会话对象注册到 RouteSession。公共 DataJdbcApplication 只提供 JDBC 通用组件，不再集中匹配工厂或创建默认 Session。注入时可使用父类型 JdbcSession 配合 `@Qualifier("postgresqlSession")`，多模块共存时不要仅按父类型猜测会话。

- 模块直接调用自己的工厂创建会话，不读取或校验 JDBC URL；引入模块和配置正确的 DataSource 是调用方责任。默认 Bean 名与组名不同，但这不表示自动选择了不同的物理数据源。
- 同时引入 mysql 与 postgresql 时，应显式创建各自会话，防止两边默认注入同一个 @Primary DataSource；示例见下节。
- 多个 DataSource 必须指定 @Primary，或者显式声明每个会话；无主候选时不会任意选库。
- 自定义 Bean 名 `postgresqlSession` 或兼容入口 `jdbcSession` 会使 PostgreSQL 默认创建退让。默认配置不再提供名为 jdbcSession 的会话，旧的按名注入需迁移或自行声明兼容 Bean。
- 会话初始化不要求连接池暴露 URL 获取方法，也不通过打开连接来识别类型；不匹配的驱动或数据库可能到执行 SQL 时才报错，初始化成功不能证明数据库类型兼容。
- 只有 postgresql 一个会话组时可以省略选组参数；多个组并存时显式选 postgresql，或设置 yulinlin.datasource.default-group=postgresql。primary 组不再自动优先，需要时显式配置为默认组。

### 2 复用实体和 CRUD

原有 @JoinTable、@JoinField、@JoinMeta、ModelSelectWrapper 等 API 保持不变，主键仍由应用或实体基类生成，不增加数据库自增键回填功能。完整实体与 Service 组织方式见 ORM 专题。

本节示例使用 com.yulinlin.common.model 下的便利门面，需要按需额外引入 common，或者引入已包含 common 的 starter；这不是 postgresql 模块的强制依赖。只使用公共 JdbcSession 与低层 Wrapper 时不需要它们。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>common</artifactId>
    <version>3.0</version>
</dependency>
```

下面是方法体片段；PgUser 是业务实体占位名，需已经映射到对应表。会话组必须已注册。

```java
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.model.ModelDeleteWrapper;

// postgresql 是模块默认组；使用其他自定义组时相应替换。
ModelInsertWrapper.newInstance("postgresql", user).execute();

var users = ModelSelectWrapper.newInstance("postgresql", PgUser.class)
        .eq("status", 1).orderByAsc("id").selectList();

var page = ModelSelectWrapper.newInstance("postgresql", PgUser.class)
        .eq("status", 1).orderByAsc("id").selectPage(1, 20);

PgUser patch = new PgUser();
patch.setId("existing-id");
patch.setName("updated");
ModelUpdateWrapper.newInstance("postgresql", patch).execute();

// 必须提供正确主键或明确条件；没有默认的全表写入保护。
ModelDeleteWrapper.newInstance("postgresql", patch).execute();
```

表名、列名和别名使用 PostgreSQL 双引号；普通 schema 限定名按段引用，例如 `public.pg_user`。大小写必须与实际建表一致：未引用的 PostgreSQL 标识符通常转为小写，驼峰实体字段应明确映射到实际列名，推荐数据库采用小写下划线命名。[PostgreSQL 标识符规则](https://www.postgresql.org/docs/current/sql-syntax-lexical.html)

### 3 与 MySQL 同时使用

MySQL 使用公共 JdbcSession，PostgreSQL 使用其子类 PostgresqlSession，两者沿用相同会话接口和事务实现。给不同数据库选择各自工厂，再指定组名；不要把工厂 Bean 名和路由组名混为一谈。

以下配置类假设应用已定义名为 dataSource 的 MySQL 主 DataSource 和 pgDataSource 的 PostgreSQL DataSource，主库标记 @Primary。显式声明兼容名 jdbcSession，使两个模块的默认创建退让，再用各自工厂绑定正确的数据源和组名。这里 mysqlSession 只是同一个主会话的 Bean 别名，不会重复注册对象。完整 DataSourceProperties 定义方式见多数据源专题；pgDataSource 的 URL 同样需要按编码器使用情况设置 stringtype。

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class PgSessionConfig {
    @Bean(name = {"jdbcSession", "mysqlSession"})
    public JdbcSession mysqlSession(
            @Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("dataSource") DataSource dataSource) {
        return factory.create(dataSource, "mysql");
    }

    @Bean("postgresqlSession")
    public JdbcSession postgresqlSession(
            @Qualifier("postgresqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("pgDataSource") DataSource dataSource) {
        return factory.create(dataSource, "postgresql");
    }
}
```

返回的会话 Bean 会自动注册，不在 Bean 创建方法中反向注入 RouteSession。业务使用 `ModelSelectWrapper.newInstance("postgresql", PgUser.class).selectList()` 或 `@JoinSession("postgresql")` 选库。factory.create 本身不执行路由注册，也不自动确认手动传入的数据源是否与方言匹配。

Spring @Transactional、框架 @JoinTransaction、懒加载和 setter 懒同步沿用公共实现。多个数据库或多个写连接不是 XA 两阶段提交，不能承诺跨库原子提交；多路由与 Spring 已绑定连接的边界必须按事务专题配置。

### 4 日期与 JSON

| 功能 | PostgreSQL 实现与限制 |
| --- | --- |
| 普通 CRUD、JOIN、IN、聚合 | 使用共用 SQL 解析器；自定义 SQL 表达式不自动翻译 |
| 分页 | LIMIT size OFFSET offset |
| 行锁 | FOR UPDATE；需要实际事务边界 |
| 日期分组 | date_trunc 加 to_char；支持分钟、小时、日、月、季度、年 |
| 数值间隔分组 | 先 CAST 为 numeric，避免整数除法；间隔必须大于 0 |
| 聚合 HAVING | 将所选聚合别名展开为原表达式，如 count(*)，不直接引用输出别名 |
| 分组表达式 | GROUP BY 展开原分组表达式，避免日期分组别名与原列同名时分组粒度错误 |
| 原生布尔列 | 按 JDBC 布尔类型读取，保留 false 与 NULL，不把 PostgreSQL 的 f 当作 true |
| JSON 路径读取 | CAST 为 jsonb 后用 #>> 提取文本，支持 text、json、jsonb 列中的合法 JSON |
| JSON 数字或布尔条件 | 根据条件值生成 numeric 或 boolean CAST；不自动处理脏数据 |
| JSON 局部更新 | 尚未提供自动 jsonb_set 生成，路径赋值会明确拒绝；整字段替换沿用普通更新 |

日期若沿用现有字符串编码并存为 TEXT，应统一固定格式与时区约定，才能依赖字符串范围比较。原生 timestamp/date 列的字符串写入需确认驱动类型推断与格式；日期分组会 CAST 到 timestamp，不提供按业务时区自动转换 timestamptz 的保证。

下面是低层 Wrapper 方法体片段。实体对应的 JSON 属性仍可定义为 Map、List 或普通 Bean，由编码器完成整字段读写；JSON 路径选择的结果是文本，不是自动推断的 Java 数字或对象。

```java
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;

var query = new SelectWrapper<>().table("pg_user");
query.fields().field("id", "id").field("payload->profile->name", "nickname");
query.where().eq("payload->profile->name", "alice")
        .gte("payload->age", 18)
        .eq("payload->enabled", true);
// 同一种对象路径也可用 nested：
query.where().nested("payload", nested -> nested.eq("city", "Shanghai"));

var group = new GroupWrapper<>().table("pg_user");
group.aggregations().day("created_at", "day");
group.metrics().count("*", "totalCount");
group.having().gt("totalCount", 1);
```

这些是 SQL 构造片段，不会自行执行查询；Model Wrapper 的执行方式见 ORM 专题。JSON 路径以 `->` 分段，例如 `payload->items->0->name`。不存在的路径得到 SQL NULL；数字条件遇到不能转换的字符串、非 JSON 文本列遇到非法 JSON 时，数据库会报错，不会静默跳过。JSON 对象与数组整体比较、混合类型 IN、包含查询和 JSON 索引优化不在当前自动适配范围内。[PostgreSQL JSON 操作](https://www.postgresql.org/docs/current/functions-json.html)

日期分组依赖 PostgreSQL 的 [日期函数](https://www.postgresql.org/docs/current/functions-datetime.html)及 [格式化函数](https://www.postgresql.org/docs/current/functions-formatting.html)。季度按所在季度的起始月分组。别名、原始 SQL、表达式中的表名或函数是可信代码，不可由未校验的 HTTP 参数直接拼接。

### 5 大集合写入

普通集合写入复用 JDBC executeBatch。`.batch()` 仍按公共能力判断是否启用多连接，默认最多 4 个连接、每次 executeBatch 256 条。PostgresqlSession 不重写这套连接、事务和批处理流程。

可在 URL 中显式添加 `reWriteBatchedInserts=true`，让 pgJDBC 将兼容批量 INSERT 改写为多值 INSERT；并非框架替你开启，也不是所有 SQL 都能改写。[pgJDBC 参数说明](https://jdbc.postgresql.org/documentation/use/)

多连接不保证比单连接更快。需要单库严格原子事务时配置 parallel-connections 为 1；驱动返回 SUCCESS_NO_INFO 时只统计成功命令，不承诺精确受影响行数。连接池容量、索引、冲突、磁盘与 WAL 都会影响实际性能。

byte[] 参数由驱动绑定；InputStream 在 PostgreSQL 下使用 setBinaryStream，目标通常是 bytea，不沿用 MySQL Blob 绑定。除 PostgreSQL 原生布尔列按布尔类型读取外，现有 ORM 查询结果沿用字符串编码器解码，尚不承诺 bytea 或 PostgreSQL oid 大对象的完整二进制读回；需要时使用专用编码器或 JDBC 读取。

### 6 迁移与验证

新代码使用：

- `com.yulinlin.jdbc.postgresql.PostgresqlSession`
- `com.yulinlin.jdbc.postgresql.PostgresqlParseManager`
- `com.yulinlin.jdbc.postgresql.PostgresqlAutoConfiguration`
- `@Qualifier("postgresqlSessionFactory")`

已移除 PostgreSQL 包内历史 MysqlParseManager、MysqlParseAutoConfig、重复解析器和分页工具，包括空继承的兼容转发类，以及独立的 SqlDialect、PostgresqlDialect。当前 PostgresqlParseManager 继承 SqlParseManager，在 init 中先调用 super.init() 注册公共 CRUD，再注册 PostgreSQL 的 NameParse、PageParse、DateParse、IntervalParse；只有确实不同的 SQL 才单独实现，普通节点不增加重复类。MySQL 同样保留 MysqlParseManager 注册自己的日期和数值区间解析器。

如果外部代码直接引用旧 PostgreSQL 类，必须迁移到上述新入口并重新编译；不要仅替换 JAR。旧 PG 工厂名 mysqlSessionFactory 必须改为 postgresqlSessionFactory；真正的 MySQL 工厂仍叫 mysqlSessionFactory。

业务仍使用原有 Model Wrapper、Request、RouteSession 和 factory.create(dataSource, group)。默认主会话 Bean 从 jdbcSession 改为 postgresqlSession，按名注入必须相应更新；默认组从 primary 改为 postgresql，显式选择旧 primary 的业务也需迁移或自行注册旧组。仅有一个组时仍支持无组参数查询；多个组时通过 default-group 配置或请求参数选库，Wrapper API 不变。额外数据源仍需显式声明对应 Session Bean，由 core 自动注册到路由。SqlParamsContext 只携带当前 ParseManager、字段解析器与请求内的 SELECT 别名，不引用 Session，也不把别名写回共享的模型映射。在注册表初始化后不再修改的前提下，同一数据库语法的多个会话可以复用一个 ParseManager；不同数据库要使用各自的注册表。

低层解析可调用 session.parseSql(node, params)，也可直接使用 new PostgresqlParseManager().parse(node, params)；两者只生成 SQL，不获取连接或执行查询。不要把公共 SqlParseManager 或 MysqlParseManager 当作 PostgreSQL 解析器。增加数据库适配时继承 SqlParseManager，在 init 中注册确有差异的 IParse 实现；仅有驱动读写差异时再继承 JdbcSession，重写 bindParameter、readColumn 并设置对应解析器。通过工厂指定 JDBC 地址前缀及 Session 构造函数，再由数据库模块自动配置直接创建自己的 Session Bean，无需公共 JDBC 工厂选择逻辑，也无需复制 CRUD 解析器或事务代码。

不需要外部数据库的专项测试：

```powershell
mvn -pl postgresql,sqlite -am `
  -Dtest=PostgresqlSessionTest,PostgresqlAutoConfigurationTest,JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest,OrmProxyIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

上述命令包含临时 SQLite 数据库测试，但不连接外部 MySQL 或 PostgreSQL。真实 PostgreSQL 测试默认跳过，手动启用：

```powershell
$env:PG_TEST_URL = 'jdbc:postgresql://127.0.0.1:5432/test_db'
$env:PG_TEST_USER = 'test_user'
$env:PG_TEST_PASSWORD = '<测试账号密码>'
mvn -pl postgresql -am -Dtest=PostgresqlServerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

PostgresqlServerTest 在一个测试连接中创建临时表；连接关闭后清理，不使用应用的数据库配置，不修改业务表。入口覆盖共用 JdbcSession 的批处理、CRUD、字符串日期与 BigDecimal、jsonb 编解码、JSON 条件、分组 HAVING、分页行锁和回滚；不覆盖真实服务中的连接池并发、跨库提交或 ORM 代理链。只有实际启用并通过后才能声称真实 PostgreSQL 集成测试通过。

---

<!-- source: doc/topics/20-transactions.md -->
## JDBC 事务：独立 Session、路由协调与 Spring 接入

> 基线：2026-10-04 事务实现；2026-10-05 按命名会话组更新示例 / JDK 25 / 制品版本 3.0 / Spring Boot 3.5。本轮未运行测试、编译或打包，历史验证结果不代表本次变更已验证。
> 源码：core 的 AbstractSession、RegisterSession、RouteSession；jdbc 的 AbstractJdbcSession、ConnectionPool、SpringTransactionAop。

### 推荐选择

- 多数据源或多连接批处理：使用框架 `@JoinTransaction` 或 `SessionUtil.route().transaction(...)`。
- 已有 Spring 管理的单数据源业务：继续使用 Spring `@Transactional`；检测到同一 DataSource 的绑定连接时，框架复用它，在原线程顺序执行，由 Spring 完成物理事务。
- 严格单库原子性：`parallel-connections: 1`，不要把多连接批处理当作单个数据库事务。
- SQLite：使用通用 JdbcSession、单连接，默认组 sqlite；无需注册 DataSource 或 sqliteTransactionManager Bean。

不要默认同时叠加两种事务注解。代理注解需要 Spring 管理的对象并经代理调用，同类自调用或 `new Service()` 不会生效。

### 框架事务示例

```java
import com.yulinlin.data.core.anno.JoinTransaction;

@JoinTransaction
public void saveBusinessData() {
    ModelInsertWrapper.newInstance("mysql", mysqlUsers).batch().execute();
    ModelInsertWrapper.newInstance("sqlite", localUsers).execute();
    // 任何未捕获的异常都触发回滚协调。
}
```

也可以直接使用回调：

```java
SessionUtil.route().transaction(() -> {
    ModelInsertWrapper.newInstance("oss", usersToInsert).batch().execute();
    return null;
});
```

单次 CRUD 请求没有外层事务时也会自动开始、完成自己的事务；多个独立请求不会自动组成一个业务事务。

RouteSession 按实际访问加入参与者，每个 Session 只加入一次；最终正常结束逐个提交，异常逐个回滚。状态按路由实例、会话实例及调用线程隔离，不再由静态计数或已注册组数决定。路由只结束自己加入的事务层，不代替业务关闭一个预先独立开启的外层 Session 事务。

### 独立 Session

配置好的 JdbcSession 可以不通过路由直接执行已构造的请求：

```java
// 方法体片段，所在方法声明 throws Exception。
// jdbcSession 是工厂已配置的对象；insertRequest 是已构造的 ExecuteRequest。
jdbcSession.startTransaction();
try {
    jdbcSession.insert(insertRequest);
    jdbcSession.update(updateRequest);
    jdbcSession.commitTransaction();
} catch (Exception | Error error) {
    try { jdbcSession.rollbackTransaction(); }
    catch (Exception | Error cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
    throw error;
}
```

也可以直接 `jdbcSession.insert(insertRequest)`，让这一请求自动管理事务。事务必须在开启它的同一线程结束。这里的独立能力指请求执行不依赖全局 RouteSession；工厂仍需注入编码器、解析器等组件，现有 Model Wrapper 的构造/execute 快捷入口仍依赖框架路由。

### 并发批处理与异常

默认并发连接上限为 4，外围配置为 `yulinlin.datasource.jdbc.parallel-connections`，按会话覆盖用 `session.setParallelConnections(n)`；完整配置和 `.batch()` 示例见多数据源专题。

支持并发写入时，上层分成最多连接上限数量的大组，一组一个任务/连接；`supportsParallelWrites()` 为 false 时不分组。底层每次 JDBC batch 默认 256 条，可用 `yulinlin.datasource.jdbc.execute-batch-size` 或 `session.setExecuteBatchSize(n)` 调整。`executeBatch()` 仅执行语句，不 commit，不缩小业务事务的回滚范围。

未绑定 Spring 事务时，异步任务明确捕获所属 Session 的连接上下文，不依赖工作线程的 ThreadLocal。每个物理连接互斥使用；等待所有已提交任务结束后，才提交、回滚或回收连接。同步/异步混合批次累加所有结果，不能因为同步批次已产生结果就提前返回。失败和连接清理异常向调用方传播，次要异常保留为 suppressed。

嵌套回滚，以及已有事务中的 JDBC 请求执行失败，会标记 rollback-only。即使业务捕获异常，外层也不能继续正常提交；外层结束实际回滚并抛出异常。嵌套是共享外层事务的计数，不是保存点回滚。

### Spring 注解：支持范围与限制

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public void saveOneDatabase() {
    ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();
}
```

jdbc 自动配置注册 SpringTransactionAop，识别类/方法的 Spring注解，开启和结束框架路由事务。若存在 Spring 原生事务管理器，其事务拦截器仍负责自己的属性。框架借用 DataSourceUtils 的绑定连接，不跨工作线程使用，也不自行提交/关闭 Spring 拥有的连接。执行失败会立即标记对应的 Spring ConnectionHolder 为 rollback-only，避免业务捕获异常后原生 Spring 先提交。

仅有 SQLite 时没有原生 JDBC 事务管理器也能使用这个注解，由框架切面管理会话；这不代表实现了 Spring 的全部事务语义。

框架切面本身不解析 propagation、isolation、rollbackFor、noRollbackFor 等属性；`@JoinTransaction.value()` 也不用于选择参与数据源。框架嵌套计数不等价于 REQUIRES_NEW、NESTED 或保存点；异步业务线程不会自动继承事务。高级传播、同步代理自动更新、Spring 与非 Spring 管理的多个库交叉参与，仍需针对实际代理顺序和业务做集成验证，不承诺透明兼容。

### 关联代理与事务

`@JoinLazy` 和 `@JoinSync` 使用的是 `SessionUtil.route()` 的事务状态，不是任意独立 JDBC 连接是否关闭 autoCommit。创建代理及访问延迟字段、调用待同步 setter 时保持同一线程的路由事务，完整用法见 `12-relations.md`。

监听器仍收到嵌套层回调，但 SyncProxyFactory 只在路由最外层提交时写回 setter 记录，内层结束不清理跟踪；实际 Session 完成后调用 `afterCompletion()` 释放上下文。回滚丢弃记录，不还原 Java 对象。

同步代理加入原生 Spring 事务时注册 `beforeCommit` 钩子，在 Spring 物理提交前写回，复用绑定连接；只读事务禁止同步 setter。代理必须在原始线程、原始路由事务内使用，原始 Spring 事务结束后也不能继续修改旧同步代理。已验证普通注解及 TransactionTemplate，不将 REQUIRES_NEW/保存点语义套到路由嵌套上。集合原地修改、null 清列、未代理对象修改不自动写回，完整边界见关联专题。

### 原子性边界

多数据源和多物理连接都是**本地事务的协调**，不是 XA/分布式原子事务。所有任务成功只是进入提交阶段，不能保证所有连接一起提交成功；中途提交失败时尝试回滚后续连接并清理全部资源，但已经成功提交的数据无法撤销。

同一 Session 使用多个连接时，提交前的跨连接查询也不保证读到其他连接的未提交写入。原子业务使用单连接；需要跨库强一致性应另选分布式事务或设计补偿，不要依据同名注解推定保证。

### 已验证与未验证

专项测试覆盖：Session 独立事务与实例隔离、默认 4/配置 2 连接限流、10 万条均匀四组、PreparedStatement 复用、256 条分批及尾批、能力关闭时不分组、后续批次失败回滚、同步/异步混合结果、失败任务排空、拒绝提交、嵌套 rollback-only、连接提交/回滚/关闭失败清理、获取连接等待不阻塞其他借用者归还、真实 Spring AOP 顺序及绑定连接复用；SQLite 真实临时文件 CRUD、512 行批次和整批失败回滚、多会话协调及编解码/建表。

代理专项测试还覆盖批量懒加载、空结果、来源数据源、setter 一次执行、默认值/null、主键和版本保护、内层提交后继续修改、缓存命中创建新代理、Spring beforeCommit 和回滚。JDBC 并发/错误注入测试使用模拟连接；SQLite 使用真实本地文件。未连接外部 MySQL，也未执行业务吞吐量或高级 Spring 传播行为基准。

```shell
mvn -pl sqlite -am -Dtest=OrmProxyIntegrationTest,JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

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
| 没有可用会话 | 检查对应模块、实际 DataSource、自动配置、唯一候选或 @Primary、已注册 group 与初始化顺序；默认组为 mysql/postgresql/sqlite，工厂不自动校验数据库类型 |
| 多个组时未指定会话 | 设置 yulinlin.datasource.default-group，或用 group 参数/@JoinSession 选库；primary 不自动优先，@Primary DataSource 不等于默认路由组 |
| 指定主从标签后没有可用节点 | 核对节点 cluster、正权重和 ping 健康结果；单个节点也不会绕过标签过滤，0 权重不参与选择 |
| PostgreSQL 提示 varchar 无法写入 jsonb 等列 | 编码器可能将值编码为字符串；每个 PG 数据源配置 stringtype=unspecified，或在自定义 SQL 中显式 CAST；见 17-postgresql |
| PostgreSQL 提示 DATE_FORMAT 或 JSON_EXTRACT 不存在 | 确认会话使用 postgresqlSessionFactory，不是 mysqlSessionFactory；统一升级 jdbc/postgresql，旧原始 SQL 不会自动翻译 |
| JoinQuery 将 username 当成固定字符串 | 动态取值写成 `${username}`，多级取值同样使用 `${user.sysRoleIds}`；见 12-relations |
| JoinLazy 字段一直为 null | 检查代理入口、路由事务、源属性及匹配数据；未开启路由事务不会自动退回立即查询 |
| 懒代理提示原始事务不一致 | 在创建代理的原始线程和事务内加载/修改，不将旧代理带入新事务；缓存命中会创建当前查询的新代理 |
| 列表关联查询太多 | 同一查询结果或一次 getLazyProxy(list) 才共享批量加载；普通 IN 按 batchSize 分批，复杂 wheres/count 仍可逐对象查询 |
| 修改关联对象但未自动写库 | 检查 JoinSync/显式同步代理、路由事务与非 null setter；集合原地修改不自动记录 |
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
