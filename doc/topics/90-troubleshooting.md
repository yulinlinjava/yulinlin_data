# 排障、交付检查与提示模板

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：对应专题列出的源码。若与实际安装版本冲突，以该版本源码为准。

| 现象/需求 | 处理 |
| --- | --- |
| `NoSuchMethodError: ReflectionUtil.property(...)` | 编译时与运行时 JAR 不一致；检查 lang/core 的来源，统一构建和依赖树，不先归咎于 JDK 25 反射 |
| 没有可用会话 | 检查对应模块、实际 DataSource、自动配置、唯一候选或 @Primary、已注册 group 与初始化顺序；默认组为 mysql/postgresql/sqlite，工厂不自动校验数据库类型 |
| 多个组时未指定会话 | 设置 yulinlin.datasource.default-group，或用 group 参数/@JoinSession 选库；primary 不自动优先，@Primary DataSource 不等于默认路由组 |
| 指定主从标签后没有可用节点 | 核对节点 cluster、正权重和 ping 健康结果；单个节点也不会绕过标签过滤，0 权重不参与选择 |
| PostgreSQL 提示 varchar 无法写入 jsonb 等列 | 编码器可能将值编码为字符串；每个 PG 数据源配置 stringtype=unspecified，或在自定义 SQL 中显式 CAST；见 17-postgresql |
| PostgreSQL 提示 DATE_FORMAT 或 JSON_EXTRACT 不存在 | 确认会话使用 postgresqlSessionFactory，不是 mysqlSessionFactory；统一升级 jdbc/postgresql，旧原始 SQL 不会自动翻译 |
| JoinQuery 将 username 当成固定字符串 | 动态取值写成 `${username}`，多级取值同样使用 `${user.sysRoleIds}`；见 12-relations |
| JoinLazy 字段一直为 null | 检查代理入口、路由事务、源属性及匹配数据；未开启路由事务不会自动退回立即查询 |
| 懒代理提示原始事务不一致 | 在创建代理的原始线程和事务内加载/修改，不将旧代理带入新事务；缓存命中会创建当前查询的新代理 |
| 列表关联查询太多 | 同一查询结果或一次 getLazyProxy(list) 才共享批量加载；普通 IN 按 batchSize 分批，复杂 wheres/count 仍可逐对象查询 |
| 修改关联对象但未自动写库 | 检查 JoinSync/显式同步代理、路由事务与非 null setter；集合原地修改不自动记录 |
| HTTP 超时配置不生效 | 区分 Spring 注入客户端、HttpUtil 静态客户端、手动构造客户端和单次覆盖 |
| `isNotFound()` 返回 false | 不足以认定成功，必要时执行请求并检查状态/异常 |
| DTO 复制类型不兼容 | 显式映射，不把 copyProperties 当作任意类型转换器 |
| 深克隆私有/final/record 报错 | 按支持边界调整模型或选择适当映射方案，不静默忽略字段 |
| 需要“最快的克隆” | 用真实模型同机 JMH，比较相同引用语义；不能宣称某工具总是最快 |

生成代码交付前检查：准确 import、实际依赖版本、表字段、无参构造器、主键/where、代理事务、分页限制、HTTP 超时/鉴权/异常、大文件流式下载、复制语义及敏感数据处理。没有执行过的构建或测试要明确标注为未验证。

## 提供给外部 AI 的提示模板

```text
请基于附件《AI接入指南》为 yulinlin-data 3.0 生成代码，运行环境为 JDK 25 + Spring Boot 3.5。
仅使用指南中已确认的 API；不要套用 MyBatis-Plus/JPA 的接口。
我的需求：[填写业务动作]
表结构与实体基类：[填写建表 SQL、IdEntity 或 SuperEntity]
写操作的定位条件与事务边界：[填写]
HTTP 接口、认证、请求/响应样例：[如涉及则填写，不提供真实密钥]
复制需求：[浅复制/同类型深克隆/不同 DTO 映射，是否需要保留共享引用]
请输出准确 imports、配置、代码和验证步骤；信息不足时先列出缺失项。
不要宣称已执行未实际运行的测试；遇到指南外接口先核查当前源码。
```
