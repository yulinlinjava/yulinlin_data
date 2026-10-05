# ORM 级联查询 懒加载和懒同步

框架自带查询结果增强：通过 ORM 查询得到的模型，会进入 `EntityProxyService`，由 `LazyProxyFactory` 处理关联查询；符合条件的关联对象再由 `SyncProxyFactory` 增加懒同步能力。通常不需要业务再次手动代理查询结果。自己 `new` 出来的聚合 DTO 则需要显式调用代理入口。

本专题说明 `@JoinQuery`、`@JoinLazy`、`@JoinSync`，以及用户、角色、菜单的关联示例。这是应用侧追加查询和代理处理，不是 SQL JOIN，也不是 JPA 的实体管理或任意对象自动持久化。

> 源码核对：2026-10-04 / JDK 25 / 制品版本 3.0。
> 来源：`core/.../session/AbstractSession.java`、`RouteSession.java`；`core/.../proxy/EntityProxyService.java`、`LazyProxyFactory.java`、`SyncProxyFactory.java`；`common/.../model/AbstractQueryModel.java`、`AbstractModel.java`。
> 代理行为已用真实 SQLite 集成测试验证；本文业务示例使用占位实体，仍需自行提供表结构和数据。事务边界同时读取 `20-transactions.md`。

## 查询后的自动处理

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

## JoinQuery 的取值规则

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

## 用户角色菜单的级联示例

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

## JoinLazy 的使用

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

### 列表批量懒加载

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

## JoinSync 的使用

懒同步指延后数据库更新，不是后台线程同步。它捕获事务内的 setter 调用，正常提交阶段再使用现有更新 Wrapper 写入数据库。

### 自动增强关联对象

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

### 显式增强普通实体

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

### 提交顺序和跟踪边界

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

## 关联会话和高级参数

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

## 交付前检查

确认 `${...}` 语法、源属性存在、关联泛型与 ID 类型、目标主键唯一性、实体映射、无参构造和可代理方法。懒加载与懒同步必须检查框架路由事务、执行线程及关联会话；对外返回前在事务内读取所需数据并组装普通 DTO，不把整个关联图默认当作接口响应。

验证入口为 `sqlite/.../OrmProxyIntegrationTest.java`：真实临时 SQLite 文件覆盖列表/单对象、分批 IN、空结果、依赖链、循环及重试、来源数据源、setter/默认值/null、嵌套事务、缓存隔离、主键/版本，以及 Spring 提交回滚。业务占位模型不属于这些测试，不代表全部文档示例已执行。未运行外部 MySQL 服务或性能基准。
