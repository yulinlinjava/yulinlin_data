# 慢操作监控页面

[文档首页](../README.md) / [监控与排障](README.md)

启用 monitor，查看慢操作统计与管理页面。

## 本页导航

- [慢数据库监控](#慢数据库监控)

## 慢数据库监控

需要在运行期查看慢 SQL 与数据库异常时，引入可选模块：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>monitor</artifactId>
    <version>3.0</version>
</dependency>
```

```yaml
yulinlin:
  monitor:
    enabled: true
    threshold: 1s
    max-records: 1000
    username: admin
    password: ${MONITOR_PASSWORD:}
```

模块默认启用；设置 `yulinlin.monitor.enabled=false` 后，不注册数据库采集器、SQL 监听器、管理接口和页面，访问 `/monitor/` 也不会得到模块页面。启用时访问 `/monitor/` 可打开内置只读页面；页面使用 Vue 3 和锁定版本的 Element Plus 2.14.7，通过 jsDelivr 加载前端资源。部署环境无法访问公共 CDN 时，应将对应资源转存到业务自己的静态资源域名，再替换模块页面中的地址。

页面可以直接打开；数据接口发现尚未验证时，页面会在原地显示账号密码对话框。验证成功后只在当前 HTTP Session 中保存一个布尔标记，再自动加载监控数据，不跳转登录页，也不依赖任何安全框架。账号默认是 `admin`，密码没有默认值，必须通过配置或环境变量提供；密码为空时登录接口拒绝验证，避免发布一个所有人都知道的默认密码。

监控会轻量聚合所有实际执行的 JDBC 操作，以 `Group + Session 类型 + 操作类型 + 来源实体 + SQL 模板` 为一组，展示执行次数、慢查询率、平均耗时、最大耗时和最近耗时。只有达到 `threshold` 的成功操作及数据库异常才保留明细，最多保存最近 `max-records` 条；SQL 聚合组也使用该数量作为内存上限。平均耗时基于全部执行计算，不会因为只统计慢操作而被放大。

页面按照优化优先级排序：`P0` 表示出现异常；`P1` 表示平均耗时达到阈值 3 倍或最大耗时达到 10 倍；`P2` 表示平均达到 2 倍、最大达到 5 倍或慢查询率达到 50%；`P3` 表示平均达到阈值、最大达到 2 倍或慢查询率达到 10%；`P4` 表示只有少量请求超过阈值；其余为正常。慢查询率参与等级判断前至少需要 10 次执行，避免首次慢请求直接产生过高等级。同等级依次比较异常次数、慢操作次数、慢查询率、平均耗时和最大耗时。

实体请求会显示来源实体、完整类名以及 `@JoinTable` / `@JoinTableList` 声明的表。自定义 SQL 的 `fromClass` 为 `null` 或 `Object.class` 时，来源显示“自定义 SQL”、表名显示 `-`，不会通过正则猜测表名。批量写入每个执行组记录一次；SQL 只保留模板，不采集绑定参数。多数据源按 `Session.group()` 和具体 Session 类型区分，`RouteSession` 不重复计数；缓存命中没有访问数据库，因此不会生成记录，延迟写队列最终执行 JDBC 时会自然纳入监控。

页面接口和 Java API 都只读查询监控数据；`DatabaseMonitor.clear()` 仅供应用内部按需调用。

---

下一步：返回[监控与排障主题目录](README.md)按需选择相关专题。
