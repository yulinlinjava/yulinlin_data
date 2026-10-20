# ORM 开发

[返回文档首页](../README.md)

实体、CRUD、动态查询、SQL 与 Repository。

## 按需阅读

| 小专题 | 解决什么问题 |
| --- | --- |
| [实体映射与最小模型](entity.md) | 定义 DemoUser，后续 CRUD 示例沿用这个模型。 |
| [插入、更新与删除](crud.md) | 完整 CRUD Service，以及写入和查询缓存的交互。 |
| [查询条件、分页与结果](query.md) | 使用 Wrapper 与查询条件对象构建页面查询。 |
| [全文检索与高亮](search.md) | 统一 match/highlight API、中文分词和数据库差异。 |
| [SQL JOIN](join.md) | SQL 联表查询；与应用侧 JoinQuery 关联加载区分。 |
| [自定义 SQL](raw-sql.md) | QueryRequest 和 ExecuteRequest 的参数化原始 SQL。 |
| [统计分析](analytics.md) | 指标、分组、日期筛选和统计结果模型。 |
| [请求与结果过滤器](filters.md) | 通过 RouteSession 增强修改表名、请求和结果。 |
| [Repository 接口](repository.md) | 方法查询、缓存注解、多数据源与 Service 职责。 |

## 建议顺序

[实体映射与最小模型](entity.md) → [插入、更新与删除](crud.md) → [查询条件、分页与结果](query.md)。已有明确问题时直接选择对应小专题，不必按顺序读完。

只维护小专题正文；本页只提供导航。给 AI 使用时先提供 [llms.txt](../../llms.txt)，再按任务加载相关页面。
