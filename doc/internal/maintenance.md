# 迁移、文档与 AI 维护约定

[文档首页](../README.md) / [内部维护](README.md)

静态检查与授权执行边界，测试源码布局和文档生成。

## 本页导航

- [迁移与排障](#迁移与排障)
- [验收与文档维护](#验收与文档维护)
- [测试代码布局](#测试代码布局)
- [AI 使用约定](#ai-使用约定)

## 迁移与排障

| 症状或旧入口 | 处理 |
| --- | --- |
| NoSuchMethodError | 比对编译/运行时 lang、core、jdbc 来源和版本；不能只替换部分 JAR |
| import jdbc.names.SqlNode | 当前类型为 com.yulinlin.jdbc.session.SqlNode |
| 原 PostgreSQL 包内 MysqlParseManager/MysqlParseAutoConfig | 使用 PostgresqlParseManager/PostgresqlAutoConfiguration |
| 原 PG 工厂名 mysqlSessionFactory | 改为 postgresqlSessionFactory，真正 MySQL 名称不变 |
| 默认 jdbcSession / primary | 默认 Bean mysqlSession/postgresqlSession，组 mysql/postgresql；自定义兼容名仍可让默认创建退让 |
| 多组默认不明确 | 设置 datasource.default-group 或显式请求组；primary 不自动优先 |
| Raw ExecuteRequest 执行返回 null | 原工厂未设置 requestType；当前源码已设置为 update |
| 原始 SQL 参数绑定为 null | Map 键使用裸名，SQL 保留 #{name}；ExpressionNodeParse 当前已修正 |
| 私有反射访问失败 | 检查模块 opens 和支持模型，不用吞异常绕过 |
| PostgreSQL 提示 MySQL 函数 | 工厂错配或原始 SQL 未迁移 |
| Git push publickey 认证错误 | 是远端认证问题，不等同于本地 commit 失败；检查实际 SSH 客户端和密钥，不修改业务代码 |

以上迁移信息依据当前源码，不表示部署产物已同步。日期格式、字段命名、JSON、原生类型、库版本和代理行为在目标环境验证。

## 验收与文档维护

以下编译、定向测试和 JMH 描述均为历史记录，不代表本次改动已经验证。本次 Session 日志/监听迁移及核心边界文档只做静态阅读检查，不执行编译、测试、打包或 `git diff --check`。历史结果、口径与复现方法见[本地数据库性能报告](../operations/local-benchmark.md)。

| 层次 | 案例 |
| --- | --- |
| 解析和入口 | RawSqlParametersTest、ExecuteRequestRawSqlTest、PostgresqlSessionTest |
| 负载均衡 | LoadBalanceTest、RegisterSessionDefaultGroupTest |
| 连接与事务 | JdbcSessionTransactionTest、JdbcBatchExecutionTest |
| SQLite 集成 | SqliteIntegrationTest、SqliteSessionTest、SqliteSchemaManagerTest、SqliteDefaultsTest：本轮定向通过；OrmProxyIntegrationTest 未纳入本轮定向命令 |
| H2 集成 | H2SessionTest：启动建表/索引和 NONE 模式本轮通过；日期/区间解析、多连接批量仍需完整回归 |
| 自动配置 | SchemaEntityScannerTest、MysqlParseAutoConfigTest 本轮通过；PostgresqlAutoConfigurationTest 未纳入本轮定向命令 |
| 真实 PostgreSQL | PostgresqlServerTest，需显式测试环境，默认不启用 |

使用独立测试配置和隔离资源，不默认读取生产连接或修改业务表。性能测试与正确性回归分开，不用固定毫秒数作业务测试的成功断言。

### 测试代码布局

所有模块的单元测试、集成回归用例及测试辅助类统一放在 `admin/src/test/java`，测试资源放在 `admin/src/test/resources`；不再向各业务库模块添加 `src/test` 代码，也不把测试类放进 `src/main/java`。JMH 保留在 `admin/src/jmh/java`，按已有性能测试 profile 隔离。

迁移后的测试保留原 Java 包名，便于继续访问被测模块的包级成员，以及复用现有包扫描辅助模型。JUnit、Mockito 等测试工具由 `admin/pom.xml` 的 `spring-boot-starter-test` 提供；仅为测试接入的数据库、缓存模块使用 `test` 范围依赖，不进入 admin 正式运行环境，库模块不再统一继承测试工具依赖。

2026-10-19 已将 13 个库模块的 73 个测试及辅助文件迁移到 admin。此次只调整目录、依赖和忽略规则，没有执行编译、测试或打包；上表所述结果属于历史记录，不代表迁移后已经重新验证。需要真实数据库的用例仍须由使用者提供隔离环境，不能将全部测试直接指向生产库。

文档按“大主题导航 → 独立小专题”维护，小专题是唯一正文源。清单和 AI 导出范围位于 [topics.json](../topics.json)，其中 `ai: true` 的主题参与业务 AI 指南导出；内部实现、监控和完整性能报告按需另读。新增页面时同步修改主题 README、清单及必要的任务索引，不再新增旧式编号大文档或平行正文。内部维护 AI 先读[组件边界](architecture.md)，涉及具体接入再补充[扩展流程](extension.md)。维护依赖、签名、异常、null、线程/事务、复制与资源语义；示例标明完整类、方法体片段、业务占位和前置条件。

文档脚本需要 PowerShell 7 与 Node.js 18+。在文档任务授权范围内，仓库根目录执行：

```powershell
./doc/build-ai-docs.ps1
./doc/build-ai-docs.ps1 -Check
```

脚本按清单导出业务小专题；跨页链接转为 AI 合集内的唯一锚点，未导出的页面和源码链接按相对路径保留。静态检查包含页面清单、相对路径、章节锚点及代码围栏；`-Check` 只校验，不写入文件。脚本不启动应用、不编译、不运行测试，不证明示例业务正确。维护时保留稳定的小专题路径，移动文件要同步修改入站链接；AI 指南不作为第二份手写正文。

### AI 使用约定

AI 先读[版本和模块选择](../start/modules.md)，再按任务读所需小专题；框架扩展先读[组件边界](architecture.md)，再读[实现流程](extension.md)。不能套用 MyBatis-Plus、JPA、Spring Data 的接口，不能凭方法名发明参数。

默认执行边界：修改源码或文档后只做静态阅读检查，不主动执行编译、打包、单元测试、集成测试、JMH、Spring YAML 属性绑定测试或 `git diff --check`。只有用户在当前任务中明确要求时，才执行对应命令；没有执行的验证不得写成“已通过”。本文及其他专题已有的测试结果均为历史记录，不代表后续改动已经重新验证。

生成业务代码前提供表结构、模型基类、主键/where、会话 group 和事务边界。自定义 SQL 提供目标数据库；HTTP 提供接口样例而非真实密钥；复制任务提供引用关系及类型需求。未验证应明确说明，不把统计或性能结论超出样本外推。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
