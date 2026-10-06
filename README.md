# yulinlin-data

面向 Spring Boot 的自定义 ORM 和 Java 工具库。当前版本：JDK 25、Spring Boot 3.5.16、制品 3.0。不是 MyBatis-Plus、JPA 或 Spring Data。

## 七个专题

| 你要做什么 | 阅读入口 |
| --- | --- |
| 选模块、连接数据库、多数据源与默认 group | [接入与数据源](doc/01-接入与数据源.md) |
| 实体 CRUD、自定义 SQL、统计分析、批量与事务 | [CRUD 与统计分析](doc/02-CRUD与统计分析.md) |
| 用户角色菜单、JoinQuery、懒加载、懒同步 | [关联查询与代理](doc/03-关联查询与代理.md) |
| HTTP、上传下载、超时、反射、深克隆、JSON | [工具类](doc/04-工具类.md) |
| 新数据源模块、架构、排障、验收与历史性能 | [扩展开发与维护](doc/05-扩展开发与维护.md) |
| JSON 接口请求解密、响应加密与浏览器协议 | [接口安全](doc/06-接口安全.md) |
| SQLite/H2 多线程写入结果、选型依据与复现 | [SQLite/H2 性能报告](doc/07-SQLite与H2性能报告.md) |

[文档目录](doc/README.md)提供完整阅读导航。AI 能读取仓库时从 [llms.txt](llms.txt)进入；只能上传一个附件时用 [AI 接入指南](doc/AI接入指南.md)，其中包含五个使用专题。

## 快速选择

- MySQL ORM：starter + mysql。
- 本地 SQLite：starter + sqlite，默认文件 data/local.db、group 为 sqlite，单写连接；适合单文件和可串行写入。
- 本地 H2：starter + h2，默认数据库基路径 data/local、group 为 h2，默认最多 4 个连接；适合多线程小批量写入。SQLite/H2 可在启动时递归扫描配置包，为显式 `autoSchema=true` 的完整实体创建表、文本列说明与 `JoinIndex`；MySQL 也可启用同一机制。字符串主键默认且最多 128 字符。`autoSchema` 默认 false，CRUD 阶段不再根据 `fromClass` 执行 DDL；简要实测见[第一专题](doc/01-接入与数据源.md#sqliteh2-选型)。
- PostgreSQL：postgresql；common/starter 按使用门面需要引入。
- HTTP：core；反射、深克隆和 JSON：lang。
- 接口报文加密：security；当前提供显式注解启用的 AES-256-GCM JSON 加解密。

完整依赖和配置见第一专题。制品来源由团队提供，不假设已发布 Maven Central；业务不依赖 admin 示例模块。

## 使用约定

写入先确认主键/条件，框架不默认拦截全表操作。多库或多连接是本地事务协调，不是分布式原子提交。多会话组用显式 group 或 yulinlin.datasource.default-group；primary 不自动优先。

每个专题按“入口与示例 → 常见场景 → API/配置 → 使用限制”组织。七份正文是唯一维护源，AI 单文件由五份使用专题派生，不再维护旧专题、存档或独立重复指南。

本轮文档核对日期为 2026-10-06；已运行本地库 JMH、JDK 25 编译，以及启动扫描/MySQL 自动配置/SQLite/H2 Schema 定向测试。维护与验证方式见第五专题。许可证见 [LICENSE](LICENSE)。
