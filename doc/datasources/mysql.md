# MySQL 接入

[文档首页](../README.md) / [数据源](README.md)

配置 MySQL 数据源和默认会话。

共同规则：[自动建表与索引](schema.md) · [多数据源与默认组](routing.md) · [SQL 与事务日志](../operations/sql-log.md)。

## 本页导航

- [MySQL 接入](#mysql-接入-1)
- [索引选择与 useIndex](#索引选择与-useindex)

## MySQL 接入

加入依赖：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mysql</artifactId>
    <version>3.0</version>
</dependency>
```

配置自己的数据库账号。下面的地址和环境变量是示例，不应直接用于生产：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

yulinlin:
  mysql:
    log: true
    log-params: false
    log-sql-pretty: true # 默认开启日志 SQL 美化，实际执行 SQL 不变
    max-param-length: 256
    slow-sql-threshold: 1s
    map-underscore-to-camel-case: true
    parallel-connections: 4
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
```

正常 Boot 自动配置链下无需框架启用注解。mysql 模块创建 `mysqlSessionFactory` 和 `mysqlSession`，默认 group 为 `mysql`。为兼容已有生产项目，`schema-mode` 默认 `NONE`；不需要框架管理结构时删除整个 `yulinlin.mysql` 段并由迁移脚本准备表。设为 `CREATE` 后，启动时递归扫描 `schema-packages`，创建缺失的表、普通列和声明索引，并校验已有结构；`VALIDATE` 只校验，缺失直接阻止启动。完整实体与 CRUD 见 [ORM 开发](../orm/README.md)。

## 索引选择与 useIndex

查看自动选择的执行计划可调用 `query.explain()`；查看分页总数的计划使用 `query.explainCount()`。两者返回数据库原生计划行，只执行普通 EXPLAIN，不启用 ANALYZE，详见[查看执行计划](../orm/query.md#查看执行计划)。

MySQL 模块由数据库优化器自动选择索引。统一查询 API 的 `.useIndex("idx_name")` **只对 H2 生效**；MySQL 接受合法提示并忽略，不生成 `USE INDEX` 或 `FORCE INDEX`，不因调用此方法或提示中的索引不存在而报错。这是框架的兼容策略。

普通查询、分组、分页及派生 count 均遵循这一规则，生成的 SQL 和绑定参数与未指定提示时一致。业务仍需建立合适的索引；`@JoinIndex` 的声明、创建与校验规则不变。非法参数和真实数据库错误仍按原规则处理；完整用法、请求复制及缓存行为见 [H2 索引提示](h2.md#单次查询索引提示)。

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
