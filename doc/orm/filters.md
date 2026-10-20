# 请求与结果过滤器

[文档首页](../README.md) / [ORM 开发](README.md)

通过 RouteSession 增强修改表名、请求和结果。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [请求与结果增强（过滤器）](#请求与结果增强过滤器)

## 请求与结果增强（过滤器）

实现 `IFilter` 或 `IRequestFilter` 并注册成 Spring Bean，即可自动加入过滤链。`order()` 从小到大执行，同序保留注册顺序。过滤器是共享实例，不要将当前用户、请求或分表名存入实例字段。

增强统一由 RouteSession 执行，具体 Session 和 JdbcSessionFactory 不再注入过滤器管理器：

```text
RouteSession.before → 最终 Request/数据源 → Session 解析、缓存/数据库、解码/代理 → RouteSession.after
```

`before` 在 SQL 解析和缓存 Key 构造之前执行，可以原地修改节点，也可以返回新的同类 Request，用于动态表名、租户条件或数据源选择。查询仍须返回 QueryRequest，写入仍须返回 ExecuteRequest，允许将删除转换为逻辑更新，不能返回 null。以下为配置方法片段，DemoUser 是本文示例实体，固定后缀仅演示可信的业务分表规则：

```java
@Bean
public com.yulinlin.data.core.filter.IRequestFilter userTableFilter() {
    return new com.yulinlin.data.core.filter.IRequestFilter() {
        @Override
        public QueryRequest selectBefore(String group, QueryRequest<?> request) {
            if (request.getFromClass() != DemoUser.class) return request;
            var node = request.getWrapper();
            if (node instanceof com.yulinlin.data.core.wrapper.ICountWrapper count) {
                node = count.getWrapper();
            }
            if (node instanceof com.yulinlin.data.core.wrapper.impl.AbstractSelectWrapper<?, ?, ?, ?> select) {
                select.table("demo_user_202610");
            }
            return request;
        }
    };
}
```

表名必须来自服务端可信规则，不能直接拼接客户端输入。改表名不改变 `fromClass`，它仍负责实体映射和编解码。插入、更新、删除、聚合同样修改各自的节点，批量请求须处理全部节点。替换查询节点优先使用 `request.copy(newWrapper)`，保留模型、fromClass、参数、路由、缓存和代理选项。最终请求改变 group 或 master/slave 时重新选库；后续 SQL、Key 和失效范围使用最终请求。

| 入口 | `IFilter.after` 收到/返回 | `IRequestFilter` 默认分发 |
| --- | --- | --- |
| select、group | List | 调用一次 selectAfter |
| page | Page | 对 Page.list 调用一次 selectAfter；替换列表时保留 total、extra |
| count | Integer | 保持计数不变；需要增强时覆盖普通 IFilter.after |
| insert、update、delete | Integer | 调用 updateAfter |

`after` 的新结果会真正返回给调用者；普通 IFilter 可以替换整个 Page。返回值须符合入口类型，计数和影响行数不能为 null；不要伪造乐观锁校验依赖的影响行数。分页内部 count/select 不重复增强。缓存命中同样执行 after，基础缓存不会保存后处理结果或事务代理；分页过滤当前页列表不重算数据库 total。

延迟队列入队前执行一次 before 并保存增强后节点，后台刷新不再次改表名或选库；after 只在实际执行后调用。后台写后过滤器的 RuntimeException 只记录中文日志，保留实际执行结果，不因此重试已经执行的 Node。普通写入后处理失败则向外传播，并标记路由事务回滚。

after 不代表事务已经提交，提交成功通知使用 `TransactionUtil.afterCommit`。路由作用域按实际入栈次数清理，复用 Request 或过滤器异常不会通过旧 `sessionChange` 标记误弹外层路由。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
