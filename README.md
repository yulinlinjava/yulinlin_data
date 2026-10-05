# yulinlin-data

面向 Spring Boot 的自定义 ORM 与 Java 工具库：模型 CRUD、JDBC/MySQL/PostgreSQL/SQLite 会话、同步 HTTP、反射与深克隆、JSON 等。

当前工程：**JDK 25 / Spring Boot 3.5.16 / 制品版本 3.0**。不是 MyBatis-Plus 或 JPA，不能混用它们的 API。

## 从这里开始

| 使用方式 | 入口 |
| --- | --- |
| 开发者查用法 | [文档目录](doc/README.md) |
| AI 能读取整个仓库 | [llms.txt](llms.txt)，按任务加载专题 |
| 给外部 AI 上传一个文件 | [AI 接入指南](doc/AI接入指南.md)，自动生成的完整上下文 |
| ORM 接入 | [依赖、实体、CRUD](doc/topics/10-orm.md) + [Spring 事务](doc/topics/20-transactions.md) |
| 级联与代理 | [JoinQuery、JoinLazy、JoinSync](doc/topics/12-relations.md)：自动增强、用户角色菜单、事务与同步限制 |
| 本地 SQLite | [文件配置、WAL、实体扫描建表、CRUD 与多库路由](doc/topics/16-sqlite.md) |
| PostgreSQL | [接入、方言、MySQL 共存、日期与 JSON](doc/topics/17-postgresql.md) |
| HTTP | [JSON、表单、上传下载、超时与 404](doc/topics/30-http.md) |
| 工具类 | [反射与深克隆](doc/topics/40-reflection.md)、[JSON 与其他工具](doc/topics/50-utilities.md) |
| 报错排查 | [排障与交付检查](doc/topics/90-troubleshooting.md) |

## 模块怎么选

| 需求 | 模块 |
| --- | --- |
| Spring Boot + MySQL ORM | `com.yulinlin:starter:3.0` + `com.yulinlin:mysql:3.0` |
| Spring Boot + 本地 SQLite | `com.yulinlin:starter:3.0` + `com.yulinlin:sqlite:3.0` |
| Spring Boot + PostgreSQL ORM | `com.yulinlin:starter:3.0` + `com.yulinlin:postgresql:3.0` |
| HTTP | `core`，引入 starter 时已传递提供 |
| 反射、深克隆、JSON | `lang` |
| 实体基类与公共模型 | `common` |
| 演示及 JMH | `admin`，不是业务接入依赖 |
| 其他数据库 | 相应扩展模块，先核查实现与兼容范围 |

制品仓库地址由团队提供，不假设已发布 Maven Central。HTTP 工具位于 core；core 同时包含 ORM 自动配置，不是独立的纯 HTTP starter。

## 使用前须知

- 主键注解是 `@JoinMeta(primaryKey = true)`，不要使用旧案例的 `@JoinPrimary`。
- 已兼容 Spring `@Transactional`；多路由和异步连接路径有不同边界，见事务专题。
- 写入前校验主键或条件，不假设全表写入被自动拦截。
- HTTP 默认客户端超时 10 秒；`isNotFound() == false` 不代表请求成功。
- 深克隆不是不同 DTO 类型映射；逐条克隆和整体克隆的共享引用语义不同。

## 参考与维护

[性能实测报告](doc/深度克隆性能对比报告.md)只代表当时的模型、机器与参数；[JMH 运行说明](doc/深度克隆JMH基准.md)按需读取。
[历史案例](doc/archive/README.md)已与当前指南隔离，不建议直接交给 AI 生成代码。

只维护 `doc/topics/` 中的使用契约；单文件指南由脚本生成：

```powershell
./doc/build-ai-docs.ps1
./doc/build-ai-docs.ps1 -Check
```

文档状态、核对基线和维护规则见 [文档目录](doc/README.md)。许可证见 [LICENSE](LICENSE)。
