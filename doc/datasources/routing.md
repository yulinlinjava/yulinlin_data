# 多数据源与默认组

[文档首页](../README.md) / [数据源](README.md)

注册多个数据源，使用 group 或 JoinSession 选择路由。

## 本页导航

- [多数据源注册](#多数据源注册)
- [两个 MySQL 数据源](#两个-mysql-数据源)
- [MySQL 与 PostgreSQL 共存](#mysql-与-postgresql-共存)
- [默认会话组](#默认会话组)

## 多数据源注册

三个名字不要混用：

| 名称 | 作用 |
| --- | --- |
| DataSource Bean 名 | Spring 注入具体连接池 |
| Session Bean 名 | 容器管理会话对象 |
| group | 框架运行请求时选库 |

工厂 `create(dataSource, group)` 返回已配置的 Session，并继承该工厂所属模块的配置，但不注册路由。把它声明为 EntitySession Bean，core 就会收集到 RouteSession；无需在 Bean 创建方法中反向注入 RouteSession。某个同类 Session 需要独立参数时，可调用 `create(dataSource, group, properties)` 显式覆盖。

### 两个 MySQL 数据源

下面是完整配置类。应用提供 spring.datasource 和 oss.datasource 两套 DataSourceProperties 配置；main 数据源标记 @Primary，使模块默认 mysqlSession 使用它：

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MultiDataSourceConfig {
    @Bean("mainProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainProperties() { return new DataSourceProperties(); }

    @Bean("dataSource")
    @Primary
    public DataSource mainDataSource(@Qualifier("mainProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossProperties")
    @ConfigurationProperties("oss.datasource")
    public DataSourceProperties ossProperties() { return new DataSourceProperties(); }

    @Bean("ossDataSource")
    public DataSource ossDataSource(@Qualifier("ossProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossSession")
    public JdbcSession ossSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("ossDataSource") DataSource dataSource) {
        return factory.create(dataSource, "oss");
    }
}
```

Boot 可能因为额外的 DataSource Bean 不再创建原默认源，因此例子明确声明两个池，而不是只声明第二个源。

### MySQL 与 PostgreSQL 共存

模块不检查 JDBC URL。两边自动配置可能注入同一个 @Primary DataSource；默认 group 不同不代表已绑定不同物理库。

以下完整配置类假设 MySQL 的 dataSource 与 PostgreSQL 的 pgDataSource 已正确创建。主会话增加兼容名 jdbcSession，让双方默认会话创建退让；别名 mysqlSession 指向同一个对象：

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MixedSessionConfig {
    @Bean(name = {"jdbcSession", "mysqlSession"})
    public JdbcSession mysqlSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("dataSource") DataSource dataSource) {
        return factory.create(dataSource, "mysql");
    }

    @Bean("postgresqlSession")
    public JdbcSession postgresqlSession(@Qualifier("postgresqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("pgDataSource") DataSource dataSource) {
        return factory.create(dataSource, "postgresql");
    }
}
```

MySQL 与 SQLite 共存时，保留原主库配置即可，SQLite 使用内部池和独立 sqlite 组。多个主 DataSource 没有唯一候选时，全部会话显式创建，不任意猜测。

使用容器管理的 JdbcSessionFactory：`create(...)` 返回前已初始化编码器、解析器、代理服务、日志、配置、group 和缓存；未引入缓存实现时使用空缓存，不影响启动。RouteSession 注册时只登记节点，不再补充或覆盖 Session 配置；过滤器只由 RouteSession 执行。不要把手动 `new JdbcSessionFactory(...)` 当成已完成容器注入的工厂。`create(DataSourceProperties, group)` 会新建池，资源所有者仍须负责关闭，不自动等价于独立 DataSource Bean。

## 默认会话组

只注册一个 group 时，无组参数请求自动使用它；有多个 group 时设置应用共享 LoadBalance 的默认组：

```yaml
yulinlin:
  datasource:
    default-group: mysql
```

代码设置的业务方法体片段，loadBalance 为注入的 `com.yulinlin.data.core.loadbalan.LoadBalance`：

```java
loadBalance.setDefaultGroup("mysql");
String configured = loadBalance.getDefaultGroup();
String effective = loadBalance.defaultGroup();
```

getDefaultGroup 返回配置值，defaultGroup 返回实际默认组；单组时配置并非必需。多组未配置或默认组不存在会报错。primary 是普通组名，不自动优先。

路由优先级为：请求显式 group → 方法 `@JoinSession` → Bean 类或 Repository 接口 `@JoinSession` → 模型 `@JoinSession` → 当前普通会话上下文 → 负载均衡默认组。选择方式举例，DemoUser 定义见[最小实体](../orm/entity.md)：

```java
ModelSelectWrapper.newInstance("oss", DemoUser.class).selectList();
```

也可在 Spring Service、Repository 接口或其方法上使用 `com.yulinlin.data.core.anno.JoinSession("oss")`，必须通过 Spring Bean 调用。方法注解覆盖类或接口注解，内层调用结束后恢复外层路由；框架默认 LoadBalance Bean 会绑定 YAML，自定义 Bean 应自行配置。

单个可用节点直接返回，但仍检查 cluster、正权重和健康状态；同组多节点继续按权重选择。健康缓存不会把离线默认库悄悄切到另一个组。运行时修改默认组或移除资源应协调在途事务。

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
