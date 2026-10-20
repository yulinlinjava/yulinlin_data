# 模块选择与版本

[文档首页](../README.md) / [快速开始](README.md)

先确定需要引入的模块；业务不依赖 admin 示例模块。

## 本页导航

- [模块选择](#模块选择)

## 模块选择

| 使用场景 | 依赖 |
| --- | --- |
| Spring Boot 和 MySQL ORM | `com.yulinlin:starter:3.0` + `com.yulinlin:mysql:3.0` |
| Spring Boot 和 SQLite ORM | starter + sqlite |
| Spring Boot 和 H2 本地 ORM | starter + h2 |
| Spring Boot 和 PostgreSQL ORM | postgresql；需要便利模型门面时再加 common 或 starter |
| HTTP 工具 | core，starter 已传递引入 |
| 反射、深克隆、JSON | lang |
| JSON 接口请求/响应加密、限流、权限协议 | security；Web 能力需要 Spring MVC，独立于 ORM 数据库模块 |
| 用户登录与请求身份恢复 | security-jwt（无状态）或 security-sa-token（有状态）二选一，自动依赖 security；业务提供 UserStore，见[安全选型与扩展](../security/modules.md#jwt-与-sa-token-如何选择) |
| 慢数据库操作与内置管理页面 | monitor；依赖公共 JDBC 执行入口和 Spring MVC |
| 增量同步 / MySQL、PostgreSQL 全量续传 | data-sync + 源/目标数据库模块；默认关闭，依赖公共 jdbc，不自带数据库驱动和 Web 页面 |
| 缓存公共协议与 `CacheClient` | cache-core；由 core 和具体缓存 Provider 传递引入，业务通常不需要单独声明 |
| 可选查询缓存 | cache-caffeine、cache-ehcache、cache-redis 可共存；单实现自动选择，多个实现用 yulinlin.cache.default 指定 ORM 默认缓存，客户端可指定名称 |
| 实体基类、Model Wrapper、树和 ID | common |
| 示例或基准 | admin，不作为业务依赖 |

依赖应来自团队制品仓库或同一份源码的发布产物，不假设已发布 Maven Central。同组模块保持相同版本和构建来源。Lombok 示例需要业务项目提供 Lombok 与注解处理；不使用它时手写 getter/setter。

core 同时包含 ORM 自动配置，不是独立的纯 HTTP starter。只使用公共 JDBC Session 时，数据库模块不强制依赖 common/starter。

---

下一步：返回[快速开始主题目录](README.md)按需选择相关专题。
