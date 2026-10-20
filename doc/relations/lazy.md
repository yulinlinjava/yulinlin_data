# 懒加载与批量预加载

[文档首页](../README.md) / [关联与事务](README.md)

首次访问触发加载；主键关系按 IN 批量预加载。

## 本页导航

- [懒加载](#懒加载)
- [列表批量预加载](#列表批量预加载)

## 懒加载

在上面的三个关联字段上各加 JoinLazy；其余类结构不变：

```java
import com.yulinlin.data.core.anno.JoinLazy;

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

在创建它的同一线程、同一框架业务事务中读取所需字段：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            RouterDetails details = SessionUtil.route()
                    .getLazyProxy(new RouterDetails(username, loginType));
            var user = details.getUser();
            var roles = details.getRoles();
            var menus = details.getMenus();
            // 在事务内将所需字段复制到普通响应 DTO。
            return null;
        }));
```

也可在 Spring Service public 方法上用 JoinTransaction，经代理调用。业务事务统一通过 RouteSession 开启，事务注解也接入同一入口。具体 JdbcSession 只处理自己的连接资源，不再反向调用管理器或独立创建业务上下文；关联查询与代理写回均通过 RouteSession 解析来源组和执行。

- 没有框架业务事务时，JoinLazy 字段会被跳过，不自动改成立即查询。
- getter 才触发加载，直接访问字段或原始对象不是等价调用。
- 未加载字段在事务外、其他线程或新事务读取会报错；已加载数据可在事务后读取。
- 不直接把未加载代理交给 Controller JSON 序列化或异步任务。
- 代理类不能是 final/record；需要无参构造，相关 getter/setter 可被 CGLIB 覆盖。
- 一次成功加载包括 null/空结果，不反复查询；失败不标记完成，可重试；显式 setter 值不被后续批量赋值覆盖。
- 循环懒加载会报错，仍应避免循环图。

## 列表批量预加载

普通关联会汇集同批父对象的关联键，去重后按 IN 分批查询，再按目标属性建立索引分配。默认 batchSize 为 512，表示每批去重键数，不是结果行数或事务提交大小。

同一次 selectList 的懒代理共享上下文，首次访问某个关联 getter 时为同批对象加载该字段，不逐个父对象发查询。手动 DTO 列表一次性传入：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            var details = SessionUtil.route().getLazyProxy(
                    java.util.List.of(new RouterDetails("alice", "password"),
                                      new RouterDetails("bob", "password")));
            details.getFirst().getUser(); // 汇集 alice/bob 查询并分配。
            details.get(1).getUser();     // 这一批已加载，不再单独查 bob。
            return null;
        }));
```

这段使用的是加过 JoinLazy 的 RouterDetails。不要逐个 getLazyProxy(dto)，否则不是同一批。不同查询结果不自动合并。

`@JoinQuery(value = "${ids}", batchSize = 256)` 可调每批 IN 键上限。关联集合不保证输入 ID 顺序；复杂 wheres/model 分支仍可能 N+1，不把全部关联规则都描述成自动 IN。

---

下一步：返回[关联与事务主题目录](README.md)按需选择相关专题。
