# 维护检查清单

[文档首页](../README.md) / [内部维护](README.md)

修改框架的检查项及日志命名迁移。

## 本页导航

- [维护与 AI 检查清单](#维护与-ai-检查清单)
- [日志类命名与迁移](#日志类命名与迁移)

## 维护与 AI 检查清单

1. 先识别改动属于入口编排、公共执行流程、事务生命周期、节点解析、类型转换还是数据库资源；不要将所有逻辑堆到 RouteSession。
2. 同时查看查询、count、分页、立即写入、queue 和并行批次是否经过改动入口，避免只修单一路径。
3. before 必须先于 SQL/缓存 Key 生成；after 不等于 afterCommit，事务回调不能由日志或监控代替。
4. 查询缓存发生在代理增强之前；提交前 flush 代理，提交后处理缓存失效；不得缓存或跨线程复用事务代理。
5. 具体 Session 不得反向协调业务事务；它只管理自己的连接/客户端资源。工厂应完整初始化依赖，路由注册不负责补齐。
6. 数据库专属语法放 Parse，类型转换放本模块 CoderManager/ICoder，驱动绑定/读取放 Session；公共转换工具可复用，不让一个数据库规则影响其他数据库。
7. 日志在 Session 中直接调用辅助类，可选监听器只观察执行事件。日志级别不能控制 monitor 是否收集，日志文本变更不能改变监控接口字段。
8. Session、解析器、编码器与监听器通常被多请求共享；请求参数、别名、事务状态不能放入这些组件的普通实例字段。
9. 统一 API 不代表统一后端能力：多库本地协调不是 XA，非 JDBC 不自动获得回滚能力，批处理不是提交，内存延迟队列不是可靠消息系统。
10. 默认只允许源码/文档编辑与静态阅读检查。未经当前任务明确授权，不编译、打包、运行测试/JMH、Spring YAML 绑定测试或 git diff --check；测试代码若需新增统一放 admin/src/test。

### 日志类命名与迁移

三个普通日志辅助类统一命名为：

| 后端 | 类 | 直接调用方 |
| --- | --- | --- |
| JDBC | [JdbcExecutionLog](../../jdbc/src/main/java/com/yulinlin/jdbc/log/JdbcExecutionLog.java) | JdbcSession，MySQL/PostgreSQL/H2/SQLite 复用 |
| Elasticsearch | [EsExecutionLog](../../elasticsearch/src/main/java/com/yulinlin/elasticsearch/log/EsExecutionLog.java) | ElasticsearchSession |
| MongoDB | [MongoExecutionLog](../../mongodb/src/main/java/com/yulinlin/mongodb/log/MongoExecutionLog.java) | MongoSession |

这些类都不是 Bean，没有管理器和自动分发。SqlNodeLog、EsRequestLog、MongoRequestLog 旧名称已移除，不保留空转发兼容类；外部直接引用须更新 import。Lombok 生成的 logger 名称也跟随类名改变，使用完整旧类名配置日志级别或采集过滤的项目需要迁移；按包配置的规则不变。事务日志 TransactionLog 与执行监听接口名称不变。

内部架构任务可提供[组件边界](architecture.md)、[执行链](execution.md)和本文给 AI；需要实现新模块时再提供[扩展流程](extension.md)。业务 AI 指南只合并清单中标记导出的主题，不混入内部架构正文。本文没有运行时验证结论；修改后核对实际源码，不凭文档推断已编译或测试通过。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
