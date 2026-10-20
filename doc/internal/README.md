# 内部维护

[返回文档首页](../README.md)

仅供框架维护：组件职责、执行链、资源治理和扩展流程。

## 按需阅读

| 小专题 | 解决什么问题 |
| --- | --- |
| [新增数据源流程](extension.md) | 先核对组件边界，再实现解析器、Session 与工厂。 |
| [JDBC 模块接入](jdbc.md) | 解析器、Session、自动配置、注册和时间编解码。 |
| [非 JDBC 与资源扩展](non-jdbc.md) | 非 JDBC 协议、负载均衡和资源生命周期。 |
| [迁移、文档与 AI 维护约定](maintenance.md) | 静态检查与授权执行边界，测试源码布局和文档生成。 |
| [核心组件与职责](architecture.md) | core 组件、协议和业务边界，修改框架前先读。 |
| [RouteSession 执行链与过滤器](execution.md) | 查询、立即写入、队列及过滤器的执行边界。 |
| [Session 日志与监控监听](logging.md) | Session 直接打印日志，保留执行监听扩展点。 |
| [事务上下文与 JDBC 资源](transactions.md) | 统一上下文、代理修改、提交回调和连接释放。 |
| [维护检查清单](checklist.md) | 修改框架的检查项及日志命名迁移。 |

## 建议顺序

[组件职责](architecture.md) → [执行链](execution.md) → [维护约定](maintenance.md)。修改事务再读[事务资源](transactions.md)；接入新数据库再读[扩展流程](extension.md)与 [JDBC](jdbc.md) / [非 JDBC](non-jdbc.md) 实现。

只维护小专题正文；本页只提供导航。给 AI 使用时先提供 [llms.txt](../../llms.txt)，再按任务加载相关页面。
