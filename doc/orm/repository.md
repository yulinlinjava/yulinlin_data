# Repository 接口

[文档首页](../README.md) / [ORM 开发](README.md)

方法查询、缓存注解、多数据源与 Service 职责。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [Repository 接口](#repository-接口-1)
- [Service 与 Repository 的职责边界](#service-与-repository-的职责边界)

## Repository 接口

Repository 根据方法名生成普通 Wrapper 查询。引入模块后自动启用，不再需要扫描注解：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>repository</artifactId>
    <version>3.0</version>
</dependency>
```

未配置扫描路径时，默认从 `@SpringBootApplication`所在根包递归查找。需要限制范围或扫描多个业务模块时，在配置文件指定：

```yaml
yulinlin:
  repository:
    scan-packages:
      - "com.example.*.*.local"       # 每个 * 匹配一层
      - "com.example.**.repository"  # ** 跨任意层级
      - "${APP_REPOSITORY_PACKAGE:com.example.repository}"
```

扫描使用类元数据，不会加载路径下所有普通类。普通包名递归扫描；路径段支持 Ant 风格通配符：`*`匹配一级包，`**`匹配任意层级，`?`匹配单个字符。YAML 中包含 `*`的路径必须加引号。配置缺失时使用应用根包，而配置存在时只扫描列出的路径。

Repository 默认 Bean 名是接口简单类名首字母小写。同名接口会明确报错，不会静默覆盖；使用 `@JoinRepository("localUserRepository")`指定唯一名称。重复或重叠扫描路径会自动去重。`JoinRepository`、`JoinCache` 和 `JoinSession` 均从 `com.yulinlin.data.core.anno` 导入；Repository 模块只提供扫描与代理实现。

Repository 接口和方法都可以使用 `@JoinSession`切换数据源，方法注解优先于接口注解：

```java
@JoinRepository
@JoinSession("mysql")
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 使用 mysql/master
    DemoUser findByUsernameEq(String username);

    // 仅此方法切换到 postgresql/master，返回后恢复 mysql
    @JoinSession("postgresql")
    List<DemoUser> findByStatusEq(Integer status);

    // 指定同组从库
    @JoinSession(value = "mysql", cluster = JoinCluster.slave)
    List<DemoUser> findByTypeEq(Integer type);
}
```

Repository 必须从 Spring 容器注入后调用，不能自行创建代理。显式 Request group 仍高于注解；最终选中的 group 和 cluster 会进入查询缓存 Key，因此不同数据源不会共用查询缓存。

### Service 与 Repository 的职责边界

推荐把一个完整的业务事务放在 Service 层：Service 方法使用 `@Transactional`（或框架 `@JoinTransaction`）定义事务边界，需要 setter 自动回写时再同时添加 `@JoinSync`。Repository 主要声明查询方法、`@JoinCache` 缓存策略，以及确实需要的 `@JoinSession` 数据源路由。

```java
@JoinRepository
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    @JoinCache
    DemoUser findByIdEq(String id);
}

@Service
public class DemoUserService {
    private final DemoUserRepository repository;

    public DemoUserService(DemoUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @JoinSync
    public void rename(String id, String name) {
        DemoUser user = repository.findByIdEq(id);
        if (user == null) throw new IllegalStateException("user not found");
        user.setName(name); // 事务提交前生成部分更新
    }
}
```

`@JoinSync` 只开启查询结果的自动更新策略，不会开启事务。它可用在 Repository 查询方法上，用于精确指定某个查询返回代理对象；但 setter 仍必须发生在外层 Service 持有的原事务内。一般业务更推荐在 Service 统一使用 `@Transactional + @JoinSync`，避免把事务和更新意图分散到多个 Repository。不要只在 Repository 查询方法上开启事务后，在方法返回之外再修改实体；此时原始事务已经结束。只读查询不需要 `@JoinSync`，显式 Wrapper 查询仍可在事务内使用 `.autoUpdate()` 覆盖单次行为。

`@JoinCache` 可以标记 Repository 接口，为其中全部抽象查询方法提供默认缓存配置；方法级注解优先，可覆盖接口配置或用 `CacheMode.NONE` 关闭单个查询。父接口上的类级和方法级配置均可继承：

```java
@JoinRepository
@JoinCache(ttl = 10, unit = TimeUnit.MINUTES)
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 继承接口配置：READ_THROUGH，缓存 10 分钟
    DemoUser findByUsernameEq(String username);

    // 明确关闭该查询的缓存
    @JoinCache(mode = CacheMode.NONE)
    List<DemoUser> findByStatusEq(Integer status);

    // 方法配置完整覆盖接口配置：强制查库，TTL 改为 30 秒
    @JoinCache(mode = CacheMode.REFRESH, ttl = 30, unit = TimeUnit.SECONDS)
    DemoUser findByIdEq(String id);

    // 额外把角色表版本纳入 Key，角色表更新后该查询也失效
    @JoinCache(namespaces = "sys_role")
    List<DemoUser> findAll();
}
```

方法级注解是完整替换而不是逐项合并：上例 `REFRESH` 方法不会继承接口的 10 分钟 TTL。`ttl = -1`是默认值，表示使用 `yulinlin.cache.ttl`；显式 TTL 必须为正数。默认 `yulinlin.cache.ttl-in-key=false`，不同 TTL 的相同查询会复用 Key；设为 `true`才按 TTL 隔离。`CACHE_ONLY`只读已有缓存，未命中抛出 `CacheMissException`。未引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`安全退化为查询数据源但不保存，`CACHE_ONLY`仍然报未命中。

接口级配置只影响查询，写方法不会继承它。写方法直接添加 `@JoinCache`、TTL 非法或 namespace 为空，会在 Repository 代理创建阶段失败；已引入缓存 Provider 时，Repository 的 insert/update/delete 按 `yulinlin.cache.clear-on-write` 决定是否在提交成功后清理解析出的表缓存。需要单次覆盖时使用写 Wrapper 或 `ExecuteRequest.clearCache(boolean)`。基础对象在关联代理处理前进入缓存，懒加载和批量预加载会按关联实体单独查询并继承入口缓存模式，因此普通懒加载关系不需要额外声明关联表 namespace；`namespaces` 主要用于无法从查询节点识别全部依赖的自定义 SQL 或非懒加载聚合。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
