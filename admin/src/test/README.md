# Admin ORM 事务人工验证

直接运行 [AdminOrmTransactionTest](java/com/yulinlin/admin/jdbc/AdminOrmTransactionTest.java)。

这是使用真实内存 H2 的 JUnit 5 测试，不使用 `@SpringBootTest`，不启动 admin，也不读取业务数据源配置。每个方法使用独立临时数据库，结束后释放连接；已有 admin 数据库不受影响。不需要配置 Redis、MySQL、PG 或 JWT 密钥。

## IDEA 运行

1. 用项目配置的 JDK（当前 Java 25）导入整个 Maven 工程，等待依赖和模块加载完成。
2. 打开 `AdminOrmTransactionTest`，点击类旁边绿色按钮运行全部 14 项，或点击方法旁边按钮单独运行。
3. 查看中文场景名称和断言结果。每项结束时控制台打印“数据库最终数据”，事务日志会显示保存点与连接提交/回滚。
4. 重复键、模拟资源失效、检查约束失败是测试主动制造的异常；JUnit 全绿才表示验证通过，不要仅凭控制台出现异常栈判断测试失败。

调试时可以在 `route.transaction(...)` 的回调、`route.setRollbackOnly()`、代理 setter 和 `counters.flush()` 上打断点。

## 场景与预期

| 编号 | 验证内容 | 数据库预期 |
| --- | --- | --- |
| 01 | 正常事务，事务内读未提交隔离 | 提交后保留 a、b；只用一条写连接 |
| 02 | 外层 rollback-only | 空表；提交失败，成功回调不执行 |
| 03 | 内层 rollback-only | 只保留 outer-before、outer-after |
| 04 | 注入 SQLState 40001，业务捕获异常 | 空表；物理资源失效不能强行提交 |
| 05 | H2 当前范围内捕获普通重复键 | 保留 a、b；不是所有 SQL 错误都标记整体回滚 |
| 06 | 内层成功，外层失败 | 空表 |
| 07 | 内层自动更新回滚 | user 的 name=final、version=2；内层新代理失效 |
| 08 | 普通 List 插入失败 | 只保留之前已提交的 existing |
| 09 | 事务内 batch 失败 | 空表；使用一条写连接 |
| 10 | 事务外 batch 部分失败 | 保留 a、b、existing；本用例使用三条写连接 |
| 11 | Spring Transactional 注解兼容 | 受检异常也回滚，空表 |
| 12 | 计数器 execute 嵌套回滚 | video.views=1，内层 +2 撤销 |
| 13 | 计数器后台刷新失败后重试 | 失败时 a、b 均为 0；修正后各为 1 |
| 14 | 事务内手动 flush | 拒绝且不取走缓冲；事务外刷新后 views=1 |

04 使用注入的 JDBC 错误验证框架判断，并不证明真实数据库死锁行为；05、10 验证的是 H2，不能据此推断 PG 的失败恢复或所有驱动的批次执行顺序。

命令行从仓库根目录运行同一组：

```shell
mvn -pl admin -am -Dtest=AdminOrmTransactionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

只运行这个类，不要用 `AdminApplicationTests` 代替；后者会启动完整 admin 应用。

## 行为风控历史回放

直接运行 [AdminRiskReplayTest](java/com/yulinlin/admin/risk/AdminRiskReplayTest.java)，无需启动 Spring 或数据库，也不需要开启线上 risk.enabled。security-risk 仅以 test 依赖加入 admin。

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| 01 | 每秒获取一个不同视频 | 第 51 条请求命中 50 个极速目标 |
| 02 | 每四秒一个不同视频 | 第 200 条、首条后 796 秒命中普通快切 |
| 03 | 间隔一、三、五分钟，持续六小时 | 完整六小时后命中全天候 |
| 04 | 每小时访问十分钟、休息五十分钟 | 只有 6/36 时间段活跃，不命中全天候 |
| 05 | 自定义业务组件 | 输出自定义命中，两次回放状态独立 |
| 06 | 导入外部 JSON | 输出每个用户、组件和档位的首次命中，不预设对方一定异常 |

控制台打印总记录数、结果状态、首次命中原始行号／时间／档位／统计依据。六小时在内存中直接回放，无需等待。修改 config() 可比较阈值；回放只识别，不调用真实 RiskControl、不模拟业务限制到期，配置 BLOCK 也不会处罚真实用户。

第六项通过 IDEA 的 JUnit Run Configuration → VM options 指定：

```text
-Drisk.replay.file=C:/data/history.json
```

含空格的路径写成 `-Drisk.replay.file="C:/data/my history.json"`。未设置时第六项跳过，其他项正常运行。UTF-8 JSON 数组支持以下数据库字段名，也支持 userId、videoId、crtTime：

```json
[
  {"id":"row-1","user_id":"user-1","video_id":"video-1","crt_time":"2026-11-05 00:00:37"},
  {"id":"row-2","user_id":"user-1","video_id":"video-2","crt_time":"2026-11-05 00:00:38"}
]
```

id 可省略；存在时用于事件去重，不要用相同 id 表示不同请求。时间支持空格或 T 分隔及小数秒；示例按 Asia/Shanghai 解读无时区时间，来源不同时修改 RECORD_ZONE。导入行为类型固定为 video-play-url，也可以把业务查询结果直接映射为 ReplayEvent。

以上两条仅展示格式，样本不足以命中默认阈值。OBSERVING 表示观察不足，CAPACITY 表示容量不足，都不能解释为“已验证正常”。

另可运行 [AdminRiskControlTest](java/com/yulinlin/admin/risk/AdminRiskControlTest.java)，验证业务自己决定限制十分钟、有效限制拦截重试、到期重新观察、永久限制及人工解除。该示例使用独立内存控制器和审计存储，不加载生产 Bean 或生产数据库；生产实现需替换存储并保证多实例处置幂等。

```shell
mvn -pl admin -am -Dtest=AdminRisk*Test -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl admin -am -Dtest=AdminRisk*Test -Dsurefire.failIfNoSpecifiedTests=false -Drisk.replay.file=C:/data/history.json test
```

完整配置、接口及迁移说明见[行为风控文档](../../../doc/security/risk.md)。

## 从本地配置的数据源读取历史

填写 `admin/src/test/resources/application-risk-local.yml`，然后在 IDEA 直接运行
[AdminRiskDatabaseReplayTest](java/com/yulinlin/admin/risk/AdminRiskDatabaseReplayTest.java)。
本地配置已忽略 Git；其他开发者从[配置模板](resources/application-risk-local.example.yml)复制同名文件即可。
这是独立的历史回放数据源，不需要启动 admin 或激活 Spring profile，也不读取 application-dev.yml。

需要填写：

- `spring.datasource.url / username / password`：历史库连接，支持 MySQL、PostgreSQL、H2。
- `risk-replay.table`：历史表名；`user-column / resource-column / time-column` 默认对应 user_id / video_id / crt_time。
- `risk-replay.user-id`：留空分析全部用户，填写时只分析指定用户。
- `risk-replay.start / end`：同时留空，按 zone 读取**今天零点到本次运行时间**；也可同时填写历史区间，包含 start、不包含 end。运行途中跨过午夜不会改变查询范围。
- `risk-replay.id-column`：填写唯一事件 ID，参与去重和分页排序；仅单页小样本允许留空，多页缺少 ID 会明确报错。
- `risk-replay.zone`：无时区历史日期时间所属时区，默认 Asia/Shanghai。
- `yulinlin.security.risk.behaviors`：直接修改两档快切及全天候阈值，再次运行即可比较效果。

这里只用 ORM 普通 SELECT 和参数绑定，总数校验与所有分页使用同一个只读 REPEATABLE_READ 事务，避免读取期间的新记录导致翻页重复或遗漏；实际读取数与该快照总数不一致会停止回放。不初始化业务表、不写入历史库、不调用 RiskControl；无需配置风控审计存储的 group。
本案例面向 DATETIME / TIMESTAMP WITHOUT TIME ZONE 列；数字时间戳或带时区列需要按其语义另行映射 ReplayEvent。
`page-size` 默认 5000；`max-records: 0` 表示读取范围内全部记录，设置正数可限制本机样本量，超过上限报错而不截断。记录仍在本机内存中累计，大量数据应提供足够堆内存或缩小日期范围。每个用户独立回放，其他用户不会占用它的统计容量；每用户事件上限仍遵循风控 memory 配置，触顶会在报告中标记统计不完整。

运行后控制台列出疑似机器人；`report-directory`（默认相对运行目录的 target/risk-replay）生成 `risk-analysis.md` 和 `risk-analysis.json`，再次运行覆盖同名报告。报告含全部用户、记录数、不同视频数、首次命中时间、命中规则及统计依据，JSON 同时保留规则配置。结果分为疑似机器人、统计不完整、观察不足和未命中，不把后三种当成“确定正常”。报告只保存在本地，不改变用户限制状态。

需要只列出高请求量的命中用户时，配置 `risk-replay.report-requests-greater-than: 300`：仍分析所选范围的全部记录，控制台、Markdown 和 JSON 的用户明细只保留“命中且请求记录数严格大于 300”的用户，并按请求数降序排列。300 次本身不入选；报告保留全量汇总与实际列出人数。这个配置只筛选报告，不改变组件检测阈值；省略时保留完整用户明细。

每次回放重新观察，验证全天候规则应选足六小时以上的连续历史；只分析今天时，今天之前的记录不会作为预热数据。未填写 URL 时测试跳过；连接、字段或查询错误会直接报错；空范围输出零条记录报告。

也可从仓库根目录运行：

```shell
mvn -pl admin -am -Dtest=AdminRiskDatabaseReplayTest -Dsurefire.failIfNoSpecifiedTests=false test
```

配置文件放在其他目录时，在 IDEA VM options 或 Maven 参数中添加
`-Drisk.replay.config=C:/data/application-risk-local.yml`。修改本地配置后重新运行测试，IDEA/Maven 会复制测试资源；
使用外部文件参数时直接读取该文件。URL、账号和密码也可以填写 `${RISK_REPLAY_DB_URL}` 之类的环境变量占位符。

## 导出给页面使用的详细报告

[AdminRiskReportTest](java/com/yulinlin/admin/risk/AdminRiskReportTest.java) 读取已保存的 `risk-analysis.json`，仅重新查询名单中“命中且请求次数 >300”的用户，固定使用原报告时间范围、时区及规则，避免复核时换成新的“现在”。它调用公开的 `RiskReplay.report`，产出可直接给管理页面使用的 `RiskReport`，不修改限制状态。

在 IDEA VM options 同时填写（替换为自己的绝对路径）：

```text
-Drisk.replay.config=D:/java/yulinlin-data/admin/src/test/resources/application-risk-local.yml
-Drisk.report.source=D:/java/yulinlin-data/admin/target/risk-replay/resource-user-history/risk-analysis.json
```

也可在 Maven 命令中添加上述两个参数：

```shell
mvn -pl admin -am -Dtest=AdminRiskReportTest -Dsurefire.failIfNoSpecifiedTests=false test
```

未指定 `risk.report.source` 时跳过，不连接数据库。需保证本地配置仍指向原历史数据源和表；原始记录可能被补录或修改，因此同时输出记录数、规则首次命中时间及原始统计是否复现，供核对。

在源报告同目录生成，重复运行覆盖：

- `risk-ui-report-example.json`：第一位用户的完整 `RiskReport`，可作为单用户接口响应示例。
- `risk-ui-reports.json`：这批用户的完整报告及原始命中复现对比。
- `risk-ui-evidence.md`：快切次数、比例、最长访问段、六小时覆盖和首次命中证据。

生产代码的返回字段、比例分母、时间单位及业务 Controller 示例见[页面分析报告](../../../doc/security/risk.md#返回给管理页面的分析报告)。该测试只导出数据，不向 admin 主程序自动添加 HTTP 接口。
