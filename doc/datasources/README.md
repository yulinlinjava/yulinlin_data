# 数据源

[返回文档首页](../README.md)

按数据库接入；公共 Schema 和多数据源规则独立阅读。

## 按需阅读

| 小专题 | 解决什么问题 |
| --- | --- |
| [MySQL 接入](mysql.md) | 配置 MySQL 数据源和默认会话。 |
| [SQLite 接入](sqlite.md) | 单文件本地数据库、WAL 和单写约束。 |
| [自动建表、列类型与索引](schema.md) | 显式声明结构拥有者，再配置扫描；CRUD 不触发建表。 |
| [H2 接入](h2.md) | 配置本地 H2 和连接并发。 |
| [本地数据库选型](local-choice.md) | 先看简要取舍，完整数据与环境见性能报告。 |
| [PostgreSQL 接入](postgresql.md) | 接入 PostgreSQL 与中文全文检索前置配置。 |
| [Elasticsearch 接入](elasticsearch.md) | 索引与 Mapping 初始化、配置和能力边界。 |
| [多数据源与默认组](routing.md) | 注册多个数据源，使用 group 或 JoinSession 选择路由。 |
| [数据源配置速查](config.md) | 查询配置入口与接入限制，完整示例见各数据库页面。 |

## 建议顺序

[MySQL 接入](mysql.md) → [SQLite 接入](sqlite.md) → [自动建表、列类型与索引](schema.md)。已有明确问题时直接选择对应小专题，不必按顺序读完。

只维护小专题正文；本页只提供导航。给 AI 使用时先提供 [llms.txt](../../llms.txt)，再按任务加载相关页面。
