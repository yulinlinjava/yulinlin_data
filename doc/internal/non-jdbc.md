# 非 JDBC 与资源扩展

[文档首页](../README.md) / [内部维护](README.md)

非 JDBC 协议、负载均衡和资源生命周期。

## 本页导航

- [非 JDBC 接入](#非-jdbc-接入)
- [负载均衡与资源生命周期](#负载均衡与资源生命周期)

## 非 JDBC 接入

实现 SessionFactory<Client> 创建并完整配置 Session。通常继承 AbstractSession，实现 executeUpdate、executeSelect、executeGroup、executeCount、字段名策略及 ping/shutdown，把结果转成 IDataBuffer。与 JDBC 一样，工厂必须初始化缓存、编码器、解析器、EntityProxyService、可选执行监听器、配置和 group，不能依赖 RouteSession 注册时补齐；EntityProxyService 用于查询后的关联加载和代理增强，不能遗漏。日志在 Session.printExecution 中直接调用本模块辅助类，不注册 LogPrint。

解析器根结果用 ParseResult 携带 SDK 请求；子节点返回后端需要的对象，不强行套 SQL。客户端地址和凭据来自配置，不硬编码 localhost，不引入 jdbc 只为加入路由。Elasticsearch 模块当前基于 Java Client 9.5.4，使用默认 Rest5Client；新代码不要重新引入已弃用的 Apache HttpClient 4 RestClientTransport。

EntitySession/TransactionSession 接口不提供后端事务能力。AbstractSession 的计数也不会替 SDK 开启真实事务；当前 MongoSession、ElasticsearchSession 的开始/提交/回滚是空实现，不能照抄后宣称支持回滚。

有真实事务时管理本 Session 的资源、rollback-only、提交/回滚和清理，业务嵌套统一由 TransactionListenerManager 处理；不支持时要设计无事务路径及能力拒绝。直接把 startTransaction 改成抛异常会影响 RouteSession 的自动写入事务包装，需联动处理。

supportsParallelWrites 默认 false。异步任务必须明确传递或隔离资源和事务上下文，不能把 CompletableFuture 包装视为自动继承主线程事务。

## 负载均衡与资源生命周期

AbstractLoadBalance 只有一个注册组时直接选组；多个组使用应用共享 Bean 的 setDefaultGroup 或 yulinlin.datasource.default-group，没有配置/组不存在时报错。RegisterSession 委托同一默认规则，不保留静态 master 或取首组策略。

注册/移除发布只读 copy-on-write 快照；RandomLoadBalance 健康快照绑定注册快照，旧 ping 不恢复已移除节点。标签过滤不因单节点绕过。0 权重不参与，负权重拒绝；long 总和、[0,total) 抽样避免整数溢出和首节点偏置。

非事务请求重新选节点，路由事务内保留组和 cluster 的节点绑定。heartbeat 当前是空钩子，本轮不恢复自动后台调度；ping 是显式健康刷新。底层 JdbcSession.ping 仍主要返回 true，不代表真实服务探测已实现。

只读快照保护集合访问，不提供在途事务的资源租约。运行中随意改 group/cluster、关闭共享客户端或卸载租户仍需业务协调。

DataSource、SDK、流、执行器都需明确所有者。外部共享资源不因单个 Session 关闭被误关；内部资源应有销毁入口。JdbcSession.shutdown 主要记日志，不是连接池通用关闭器；factory.create(DataSourceProperties, group) 创建的池仍需所有者关闭。SQLite 使用 SqliteDatabase、H2 使用 H2Database 管理内部池，都不暴露 DataSource Bean。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
