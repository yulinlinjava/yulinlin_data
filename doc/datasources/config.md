# 数据源配置速查

[文档首页](../README.md) / [数据源](README.md)

查询配置入口与接入限制，完整示例见各数据库页面。

## 本页导航

- [配置速查](#配置速查)
- [接入边界与排障](#接入边界与排障)

## 配置速查

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| yulinlin.datasource.default-group | 未设置 | 单组自动；多组指定默认 |
| yulinlin.data.transaction-log | false | 全局事务诊断；记录嵌套、数据源连接提交/回滚以及 Spring 最终结果，独立于 SQL 日志 |
| yulinlin.monitor.enabled | true | 是否启用数据库操作采集及内置监控接口和页面；关闭后不注册相关 Bean |
| yulinlin.monitor.threshold | 1s | 慢操作明细保留阈值，同时作为 P1–P4 优先级的计算基准；异常始终保留 |
| yulinlin.monitor.max-records | 1000 | 慢操作明细和 SQL 聚合组各自的内存上限；超出后淘汰最早或最久未访问的数据 |
| yulinlin.monitor.username | admin | 内置监控对话框账号 |
| yulinlin.monitor.password | 空 | 内置监控对话框密码；必须显式配置，推荐使用环境变量 |
| yulinlin.`<module>`.log | false | 当前模块 SQL 成功日志；module 为 mysql、postgresql、sqlite 或 h2 |
| yulinlin.`<module>`.map-underscore-to-camel-case | true | 当前模块下划线与驼峰映射 |
| yulinlin.`<module>`.parallel-connections | 4 | 当前模块每框架事务连接上限；SQLite 最终固定为 1 |
| yulinlin.`<module>`.execute-batch-size | 256 | 当前模块一次 executeBatch 的行数，不是 commit |
| yulinlin.mysql.schema-mode | NONE | CREATE、VALIDATE 或 NONE；默认不改生产结构 |
| yulinlin.mysql.schema-packages | 空 | MySQL 启动递归扫描包列表 |
| yulinlin.postgresql.schema-mode | NONE | CREATE、VALIDATE 或 NONE；默认不改生产结构 |
| yulinlin.postgresql.schema-packages | 空 | PostgreSQL 启动递归扫描包列表 |
| yulinlin.postgresql.full-text.index-config | jiebacfg | PostgreSQL 建索引、匹配和高亮使用的分词配置 |
| yulinlin.postgresql.full-text.query-config | jiebaqry | PostgreSQL 查询分词配置 |
| yulinlin.postgresql.full-text.max-words/min-words | 40 / 15 | PostgreSQL ts_headline 摘要长度 |
| yulinlin.`<module>`.highlight.start-tag/end-tag | `__HL_START__` / `__HL_END__` | PostgreSQL/Elasticsearch 返回到原字段中的高亮边界 |
| yulinlin.`<module>`.highlight.max-fragments | 2 | 最大高亮片段数，必须大于 0 |
| yulinlin.`<module>`.highlight.fragment-delimiter | `...` | 返回多个片段时的合并文本 |
| yulinlin.sqlite.file | data/local.db | 进程工作目录下的路径 |
| yulinlin.sqlite.group | sqlite | 本地会话组 |
| yulinlin.sqlite.busy-timeout | 5000 ms | 等锁，不是查询超时 |
| yulinlin.sqlite.synchronous | NORMAL | 可选 FULL |
| yulinlin.sqlite.schema-mode | CREATE | CREATE、VALIDATE 或 NONE |
| yulinlin.sqlite.schema-packages | 空 | SQLite 启动递归扫描包列表 |
| yulinlin.h2.file | data/local | 数据库基路径，实际文件追加 .mv.db |
| yulinlin.h2.group | h2 | H2 会话组 |
| yulinlin.h2.username/password | sa / 空 | 内部文件库凭据 |
| yulinlin.h2.mode | MYSQL | 当前唯一支持的兼容模式 |
| yulinlin.h2.connection-timeout | 10s | 连接池取连接等待时间 |
| yulinlin.h2.lock-timeout | 5s | H2 锁等待时间 |
| yulinlin.h2.schema-mode | CREATE | CREATE、VALIDATE 或 NONE |
| yulinlin.h2.schema-packages | 空 | H2 启动递归扫描包列表 |
| yulinlin.h2.auto-server | false | 是否允许多 JVM 打开同一文件 |
| yulinlin.elasticsearch.log | false | Elasticsearch 成功请求日志 |
| yulinlin.elasticsearch.url | http://localhost:9200 | Elasticsearch 服务地址 |
| yulinlin.elasticsearch.map-underscore-to-camel-case | true | Elasticsearch 实体字段命名转换 |
| yulinlin.elasticsearch.schema-mode | NONE | CREATE、VALIDATE 或 NONE |
| yulinlin.elasticsearch.schema-packages | 空 | Elasticsearch 启动递归扫描实体包 |

## 接入边界与排障

- 单数据源也必须等待容器初始化，不在静态初始化块里查询。所有模块只有配置扫描包并启用相应模式时才处理结构；SQLite/H2 默认模式为 CREATE，MySQL/PostgreSQL/Elasticsearch 默认为 NONE。未扫描的结构由业务迁移工具准备。
- 不把独立库无意注册为同组节点；同组是负载均衡，不是覆盖注册。
- 多库事务与多连接事务不是 XA，详细规则见 [ORM 开发](../relations/transactions.md#事务使用)。
- 未发现会话：检查数据库模块、自动配置 imports、DataSource 候选、Session Bean 和 group。
- 表或字段不存在：核对实际建表、JoinField 映射和实体基类继承字段。
- PostgreSQL 报 MySQL 函数错误：检查是否使用了 mysqlSessionFactory 或原始 MySQL SQL。
- SQLite 原生库在 JDK 25 下可能提示 native-access 警告；部署时按实际环境配置 JVM 原生访问权限，不把警告当成数据库初始化成功的证明。
- H2 表已存在但字段报不兼容：框架不会迁移旧结构，手动迁移后重启或重建 Session；不要直接删除正在使用的 .mv.db 文件。

内部 Session 创建与扩展规则见 [内部维护](../internal/README.md)。

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
