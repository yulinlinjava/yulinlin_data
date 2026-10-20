# 新增数据源流程

[文档首页](../README.md) / [内部维护](README.md)

先核对组件边界，再实现解析器、Session 与工厂。

## 本页导航

- [架构与职责](#架构与职责)
- [RouteSession 执行链](#routesession-执行链)
- [Session 日志与执行监听](#session-日志与执行监听)
- [过滤器边界](#过滤器边界)
- [事务提交成功扩展点](#事务提交成功扩展点)
- [新增数据源流程](#新增数据源流程-1)

## 架构与职责

核心组件与业务边界已独立到[核心组件与业务边界](README.md)，后续架构规则只维护该文档。本文继续负责新数据源接入、编解码扩展、资源治理及排障。

### RouteSession 执行链

见[查询、立即写入、延迟写入与批量边界](execution.md#routesession-执行链)。

### Session 日志与执行监听

见[Session 日志与执行监听](logging.md#session-日志与执行监听)，包括 JdbcExecutionLog/EsExecutionLog/MongoExecutionLog、监控扩展及耗时口径。

### 过滤器边界

见[过滤器边界](execution.md#过滤器边界)。

### 事务提交成功扩展点

见[事务上下文与提交扩展点](transactions.md#事务上下文与提交扩展点)，包括嵌套深度、sync 代理、资源提交、afterCommit 和 Spring 注解适配。

## 新增数据源流程

1. 明确 JDBC/非 JDBC，列出 CRUD、分页、JOIN、聚合、事务、并发和类型支持。
2. 新建独立模块，继承根 POM 并加入 modules；JDBC 依赖 jdbc + 驱动，非 JDBC 依赖 core + SDK。
3. 注册确有差异的 IParse；不支持的节点明确拒绝，不静默忽略。
4. 优先复用 JdbcSession；只有执行/驱动差异才增加 Session 子类。
5. 核对 Java 编码、参数绑定、数据库类型及读取的完整往返。
6. 模块自行声明工厂与 Session Bean，添加自动配置 imports。
7. 检查 group、事务、并发、资源所有权和释放路径；不能引入 Spring 绑定连接复用。
8. 补最小用法、能力限制与验收案例；执行验证须由当前任务授权。

没有差异就直接用公共注册项。不要依赖另一个数据库模块、复制空继承类或重复工具包来“兼容”。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
