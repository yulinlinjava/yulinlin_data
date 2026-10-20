# 代理自动更新

[文档首页](../README.md) / [关联与事务](README.md)

事务内 setter 跟踪、代理生命周期及限制。

## 本页导航

- [自动更新](#自动更新)
- [会话与代理边界](#会话与代理边界)
- [修改跟踪的限制](#修改跟踪的限制)
- [常见问题](#常见问题)

## 自动更新

自动更新捕获事务内的 setter 修改，在提交前统一更新数据库，不是每次 setter 立即执行 SQL，也不是后台线程写入。现有 `ProxyChanges` 按需保存在管理器的统一 `TransactionContext` 中，代理工厂不再维护独立事务 ThreadLocal；主对象和懒加载出的关联代理共用这份记录。单次查询开启：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                    .autoUpdate()
                    .eq("id", id)
                    .selectOne();
            if (user == null) throw new IllegalStateException("user not found");
            user.setStatus(1);
            return null;
        }));
```

没有框架业务事务时，`.autoUpdate()` 会在执行 SQL 前提示“请开启事务”。setter 只保存脏字段的最终非 null 值，不为每次修改创建 Runnable；最外层提交前按数据源和实体类型批量写回，保留主键校验和乐观锁。内层提交不写回，回滚丢弃修改记录；回滚不会还原 Java 对象的内存值。这些更新不能放进 `afterCommitTasks`。

全局开启：

```yaml
yulinlin:
  data:
    auto-update: true
```

全局开启后，可更新实体的查询都必须处于事务中。单次查询可用 `.autoUpdate(false)` 明确返回普通对象。

方法或类级入口：

```java
@Transactional
@JoinSync
public void rename(String id, String name) {
    DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
            .eq("id", id)
            .selectOne();
    user.setName(name);
}
```

`@JoinSync` 只开启当前调用作用域的自动更新，不创建事务；需配合 `@Transactional`、`@JoinTransaction` 或 `RouteSession.transaction(...)`。它不再允许标在字段上。

在 Spring 项目中，推荐把 `@Transactional` 和 `@JoinSync` 一起放在 Service 的 public 业务方法上，让查询、setter 修改与提交共享同一个边界。Repository 保持查询声明，通常只配置 `@JoinCache` 和必要的 `@JoinSession`。Repository 方法也可用 `@JoinSync` 精确指定该查询返回代理，但后续 setter 必须仍位于外层 Service 的活动事务内；不建议把它当作默认分层方式。代理对象不能在创建它的事务结束后继续自动更新。只读 Service 不需要 `@JoinSync`。Repository 完整示例见[ORM 开发](../orm/repository.md#service-与-repository-的职责边界)。

主对象的关联查询继承相同策略。懒字段首次 getter 得到的实体或集合元素也是同一层代理，因此在原事务内调用关联实体 setter 同样会写回对应表。

通常直接使用 `.autoUpdate()` 或 `@JoinSync`，不再手动调用 `getSyncProxy`；旧公开方法只用于已有代码迁移。

## 会话与代理边界

字段可以指定独立会话，下面是已有 DTO 中的字段片段：

```java
import com.yulinlin.data.core.anno.JoinSession;

@JoinSession("sqlite")
@JoinQuery(primary = "id", value = "${localUserId}")
private LocalUserEntity localUser;
```

LocalUserEntity 与 localUserId 由业务提供。未指定时关联上下文保留创建时的来源组；后续 getter 即使进入其他会话栈，也使用原组。同步更新也按原组执行。

| 参数 | 当前范围 |
| --- | --- |
| order | 普通关联排序；跨批 Java 合并与数据库 collation 不一定一致 |
| batchSize | 普通 IN 的键数上限 |
| wheres | 按父对象构造复杂条件，可能逐对象查 |
| size | 正值只在 wheres/model 分支分页，不作用于普通 primary/value 分支 |
| model | 当前进入 count 分支，不是替代集合泛型的通用参数 |

### 修改跟踪的限制

- 目标必须是可更新实体，有完整非 null 主键；创建/提交校验主键，禁止代理改主键。
- 只捕获真正持久化属性的单参数 setter；普通方法、直接写字段、修改原对象不自动同步。
- null 跳过：setX(null) 不清列，并取消此前该字段的待同步值。
- 集合 add、Map.put、嵌套对象原地变化不自动跟踪，需要调用持久化属性 setter 或显式更新。
- 未调用的 setter 不产生部分更新，未修改的 0/false/初始化值不会被全部覆盖到数据库；设回原值不会产生更新。
- setter 捕获对象引用，不是深快照；setter 后原地修改同一对象可能改变最终编码值。
- 支持的 Integer/int、Long/long 版本字段按原始版本条件递增；写入数不匹配视为乐观锁失败，不承诺全部 Wrapper 都具备相同版本机制。
- 代理绑定原始线程与统一业务事务上下文，结束后不能继续 setter 修改。缓存保存未增强数据，读取时复制并创建当前查询代理，不能复用旧事务代理。

同步写回统一发生在框架物理提交前，不依赖 Spring 的提交回调。`@Transactional` 只适配到同一路由事务，目前 `readOnly=true` 会在进入业务方法前被拒绝；普通只读查询无需声明该属性。高级传播与分布式一致性仍按 [ORM 开发](transactions.md#事务使用)的边界理解。

## 常见问题

| 问题 | 检查 |
| --- | --- |
| 关联没有数据 | ${...}、源属性、泛型、键类型、目标表与会话 |
| 懒字段一直为 null | 是否经过代理入口、配置路由并处于框架业务事务中，是否访问 getter |
| 提示原始事务不一致 | 是否跨线程、事务外读取或把旧代理带进新事务 |
| 列表查询仍很多 | 是否同批入口，是否使用复杂 wheres/model 分支 |
| setter 后没更新 | 是否同步代理、原始事务、非 null 值与可靠主键 |
| 缓存命中复制失败 | 模型是否满足深克隆边界，是否包含流/连接等资源 |

实现职责与验证范围在 [内部维护](../internal/README.md)，不要把历史测试记录解释成当前全部业务示例已运行。

---

下一步：返回[关联与事务主题目录](README.md)按需选择相关专题。
