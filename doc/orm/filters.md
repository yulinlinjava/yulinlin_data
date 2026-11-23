# 请求与结果过滤器

[文档首页](../README.md) / [ORM 开发](README.md)

查询和写入使用独立接口，对象后置增强统一使用 `afterRow`，全部由 RouteSession 调用。

示例基础模型见[实体映射与最小模型](entity.md)，数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [请求与结果增强（过滤器）](#请求与结果增强过滤器)
- [查询拦截与对象增强](#查询拦截与对象增强)
- [写入拦截](#写入拦截)
- [流式查询与过滤器](#流式查询与过滤器)
- [迁移与自定义管理器](#迁移与自定义管理器)

## 请求与结果增强（过滤器）

实现 `IQueryFilter` 或 `IWriteFilter` 并注册成 Spring Bean，即可自动加入过滤链；一个 Bean 也可同时实现两者。`IFilter` 只保留 `test(group, request)` 和 `order()`，仅实现 IFilter 的 Bean 会在注册时被拒绝。order 从小到大执行，同序保留注册顺序；仅查询过滤器参与查询，仅写入过滤器参与写入，另一类的 test 也不会被调用。过滤器是共享实例，不要将当前用户、请求或分表名存入实例字段。

```text
查询：RouteSession.beforeQuery → 最终请求/数据源 → EntitySession 查询与解码 → afterRow → 返回
流式：RouteSession.beforeQuery → EntitySession.selectEach → afterRow → 业务 consumer
写入：RouteSession.beforeWrite → 最终请求/数据源 → EntitySession 执行 → afterWrite
```

| 接口 | 方法 | 约定 |
| --- | --- | --- |
| IQueryFilter | `QueryRequest<?> beforeQuery(String group, QueryRequest<?> request)` | 每个查询入口一次，SQL 解析、缓存 Key 生成之前处理 |
| IQueryFilter | `void afterRow(String group, QueryRequest<?> request, Object row)` | 每个返回对象一次；原地增强，不替换、丢弃行或改分页总数 |
| IWriteFilter | `ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request)` | 插入、更新、删除执行前处理，可将删除替换为逻辑更新 |
| IWriteFilter | `void afterWrite(String group, ExecuteRequest<?> request, int affectedRows)` | 实际执行完成通知，不修改影响行数，不等于事务提交成功 |

EntitySession/JdbcSession 不注入或调用过滤器，业务通过 Wrapper、QueryRequest 或 RouteSession 进入。普通列表的缓存、关联加载、懒加载和自动更新代理处理保持原有位置；过滤器拿到的是该入口完成这些处理后的对象。直接调用底层 EntitySession 不执行业务过滤。

## 查询拦截与对象增强

`beforeQuery` 可以原地修改节点，也可以返回新的 QueryRequest，用于动态表名、租户条件或数据源选择，不能返回 null。以下为配置方法片段，固定后缀仅演示可信的业务分表规则：

```java
@Bean
public com.yulinlin.data.core.filter.IQueryFilter userTableFilter() {
    return new com.yulinlin.data.core.filter.IQueryFilter() {
        @Override
        public QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) {
            if (request.getFromClass() != DemoUser.class) return request;
            var node = request.getWrapper();
            if (node instanceof com.yulinlin.data.core.wrapper.ICountWrapper count) {
                node = count.getWrapper();
            }
            if (node instanceof com.yulinlin.data.core.wrapper.impl.AbstractSelectWrapper<?> select) {
                select.table("demo_user_202610");
            }
            return request;
        }
    };
}
```

表名必须来自服务端可信规则，不能直接拼接客户端输入。改表名不改变 fromClass，它仍负责实体映射和编解码。替换查询节点优先用 `request.copy(newWrapper)` 保留模型、参数、路由、缓存、索引和代理选项。最终请求改变 group 或 master/slave 时重新选库；SQL、缓存 Key 和失效范围使用最终请求。写入节点的替换遵循相同规则，批量请求须处理全部节点。

| 查询入口 | beforeQuery | afterRow |
| --- | --- | --- |
| selectList、selectOne、group 的列表 | 一次 | 每条非 null 对象一次，空结果不执行 |
| selectPage | 一次 | 仅当前页每条对象一次，内部 count/select 不重复增强 |
| count | 一次 | 不执行 |
| explain、explainCount | 一次 | 不执行，保留原生执行计划 |
| selectEach | 一次 | 每条解码后、consumer 之前执行 |

例如为查询 DTO 补充展示字段，`UserView.displayName` 是 `@JoinField(exist = false)` 字段：

```java
@Bean
public com.yulinlin.data.core.filter.IQueryFilter userViewFilter() {
    return new com.yulinlin.data.core.filter.IQueryFilter() {
        @Override
        public void afterRow(String group, QueryRequest<?> request, Object row) {
            if (row instanceof UserView user) {
                user.setDisplayName("用户：" + user.getUsername());
            }
        }
    };
}
```

框架默认注册 `InitFilter`（order 为 9999），统一通过 afterRow 对实现 `IInitEvent` 的对象调用 `init()`；业务无需再手动调用。缓存命中也重新解码并增强，基础缓存不保存后处理结果或事务代理。增强顺序遵守 order，分页 total、extra 保持原样。修改自动更新代理的持久化字段仍遵守原有自动更新语义，展示数据宜放在非持久化字段或 DTO 中。

## 写入拦截

```java
@Bean
public com.yulinlin.data.core.filter.IWriteFilter writeLogFilter() {
    return new com.yulinlin.data.core.filter.IWriteFilter() {
        @Override
        public void afterWrite(String group, ExecuteRequest<?> request, int affectedRows) {
            org.slf4j.LoggerFactory.getLogger("business.write").info(
                    "group={} operation={} affectedRows={}",
                    group, request.getRequestType(), affectedRows);
        }
    };
}
```

没有影响任何行的成功写入仍通知，SQL 执行失败不通知。批量请求按实际执行请求通知，不承诺每个对象单独通知。`TableLogicFilter` 同时实现查询与写入接口，查询前添加逻辑删除条件，删除前改成更新；`SyncRequestFilter` 仅实现写入接口，不影响查询或阻止流式读取。

延迟队列入队前执行一次 beforeWrite 并保存增强后节点，后台刷新不再次改表名或选库；afterWrite 只在实际执行后调用。后台写后过滤器的 RuntimeException 只记录日志，保留实际执行结果，不因此重试已经执行的节点。普通写入后处理失败则向外传播，并标记当前路由事务回滚。事务外独立 batch 已成功执行的 SQL 不能因此撤销。

afterWrite 不代表事务已经提交，提交成功通知使用 `TransactionUtil.afterCommit`。路由作用域按实际入栈次数清理，复用 Request 或过滤器异常不会误弹外层路由。

## 流式查询与过滤器

RouteSession 组合消费者：先执行 `filterManager.afterRow(group, request, row)`，然后调用业务 `consumer.accept(row)`。底层只负责读取、解码、调用消费者和释放资源，不知道过滤器的存在，不收集完整列表。查询前仍执行一次 beforeQuery，使用最终请求解析 SQL。

新 IQueryFilter 默认支持流式查询。若对象增强依赖关联字段、懒加载或需要额外查库，应明确拒绝流式入口：

```java
@Override
public boolean supportsSelectEach() {
    return false; // 此过滤器依赖关联加载或需要额外执行 SQL。
}
```

SimpFilterManager 检查最终请求匹配的查询过滤器；未允许时，在读取任何行或调用消费者之前抛出异常。supportsSelectEach 仅属于 IQueryFilter，纯写入过滤器不参与此检查。权限和租户限制必须在读取前完成，不能用返回 true 来跳过必要过滤。

afterRow 不提供丢弃行、整列表排序或汇总语义；权限和租户过滤条件应在 beforeQuery 中加入 SQL。抛出异常时立即停止，本条不交给消费者，底层关闭读取资源并原样传播异常；已经执行的消费者副作用不会自动撤销。

afterRow、init() 同消费者一样不能重入当前 RouteSession/底层 JDBC Session 的查询、写入或事务操作。如果 init 原本会查询数据库或依赖关联增强，请继续用 selectList。流式仍不提供缓存、关联查询、懒加载、自动更新；详见[大结果集逐条消费](query.md#大结果集逐条消费)。

## 迁移与自定义管理器

`IRequestFilter`、`IEntityFilter` 已移除。查询使用 `IQueryFilter.beforeQuery/afterRow`，写入使用 `IWriteFilter.beforeWrite/afterWrite`；原来的 selectAfter 整列表处理应迁移为逐行 afterRow，不再提供额外的混合请求接口。需要按实体匹配时，直接在查询或写入过滤器中实现 test：

```java
@Override
public boolean test(String group, com.yulinlin.data.core.request.BaseRequest request) {
    Class<?> type = request.getEntityClass();
    return type != null && DemoUser.class.isAssignableFrom(type);
}
```

此例匹配 DemoUser 及其子类；只匹配某个具体类型可使用 `type == DemoUser.class`。这里的 entityClass 是返回/操作对象类型；要按底层映射实体筛选，则使用 request.getFromClass()。

`IFilter` 和 `IFilterManager` 的旧 before/after 已删除，没有兼容分发。旧 before 根据请求类别迁移到 beforeQuery 或 beforeWrite；旧 after 的对象处理迁移到 afterRow，写入通知迁移到 afterWrite。afterRow、afterWrite 都返回 void，不再提供替换整个 List/Page 或改写 count/影响行数的钩子。需要排序、汇总等整列表操作时，在业务拿到查询结果后处理。

之前试用版的 afterEach 已统一为 `IQueryFilter.afterRow`，列表和流式共享同一实现。RouteSession 对 List/Page 中的非 null 对象逐条调用管理器 afterRow，保留原容器、total 和 extra；count 和执行计划不经过对象增强。管理器不再提供 afterQuery 整结果入口。

直接自定义 IFilterManager 时分别实现 beforeQuery、beforeWrite、afterRow、afterWrite：前置方法默认返回原请求，两个后置方法默认无操作。`checkSelectEach(String group, QueryRequest<?> request)` 默认允许，自定义管理器若依赖关联加载或额外 SQL，可覆盖该方法并抛出异常。替换管理器后，过滤器和 InitFilter 的执行由自定义实现负责；一般直接注册过滤器 Bean，无需替换管理器。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
