# 项目上下文与生成代码约定

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`pom.xml`；各模块 `pom.xml`。若与实际安装版本冲突，以该版本源码为准。

1. 这是自定义 ORM，不是 MyBatis-Plus、JPA 或 Spring Data。不要生成 `BaseMapper`、`LambdaQueryWrapper`、`@Entity` 等其他框架 API 来替代本文接口。
2. 优先使用本文明确列出的入口、包名和签名。未列出的高级功能应查当前源码，不根据其他框架同名方法猜测。
3. 主键用 `@JoinMeta(primaryKey = true)`；不要沿用旧文档中的 `@JoinPrimary`。
4. 先确认表结构、实体基类、主键、筛选条件及会话，再生成写入代码。不要生成无条件更新或删除，也不要假设框架有全表写入拦截。
5. ORM 需要 Spring 上下文初始化完成、可用数据库会话与已存在的表；不要在静态初始化块中查询数据库。框架不负责根据下文实体自动建表。
6. 深克隆与 Bean 到 DTO 的类型映射不是同一能力。HTTP 的“不是 404”与“请求成功”也不是同一含义。
7. 编码之前确认实际依赖包含接口；如果缺少类或方法，先核对版本和运行时类路径，不用反射或异常吞掉掩盖版本错配。

## 模块与入口

| 需求 | 模块 | 准确入口 |
| --- | --- | --- |
| Spring Boot + MySQL ORM | starter + mysql | `com.yulinlin.common.domain.IdEntity` / `SuperEntity` |
| 实体映射 | core | `com.yulinlin.data.core.anno.JoinTable`、`JoinField`、`JoinMeta`、`JoinWhere` |
| 数据库分页结果 | lang | `com.yulinlin.data.lang.util.Page`，不是 Spring Data Page |
| HTTP | core | `com.yulinlin.data.core.http.HttpRequestClient`、`HttpUtil` |
| HTTP 响应/异常/文件 | core | 同包下 `HttpResponse`、`HttpRequestException`、`HttpFile` |
| 反射与深克隆 | lang | `com.yulinlin.data.lang.reflection.ReflectionUtil` |
| JSON | lang | `com.yulinlin.data.lang.json.JsonUtil` |
| 字符串、日期 | lang | `com.yulinlin.data.lang.util.StringUtil`、`DateTime` |
| 树与 ID | common | `com.yulinlin.common.util.TreeUtil`、`SnowflakeUtil` |
| Web 响应包装 | starter | `com.yulinlin.starter.domain.R` |

MySQL 是本文完整示例路径。仓库存在其他数据库模块，不代表本文示例与事务行为已在所有数据库验证。admin 是示例/测试宿主，不是业务接入所需依赖。
