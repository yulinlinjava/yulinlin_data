# SQLite：本地文件数据库

> 适用：JDK 25 / 制品版本 3.0。实现位于 `sqlite/`；通用 SQL 解析位于 `jdbc/.../sql/`。
> SQLite 与 MySQL 复用普通 CRUD 解析器和现有 Model Wrapper API，不需要另一套实体或 DAO。
> 2026-10-05 更新默认文件和 sqlite 会话组；引入模块即启用。本轮未运行测试、编译或打包。

## 最小接入

Spring Boot 项目添加依赖；已有 starter 时不用重复添加。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>sqlite</artifactId>
    <version>3.0</version>
</dependency>
```

仅使用 SQLite 时不需要 mysql 模块、数据库服务器、用户名或密码。引入模块即启用，无需提供 yulinlin.sqlite 配置：文件为进程工作目录下的 data/local.db，会话组为 sqlite。下面是可选的覆盖配置，未设置的字段沿用默认值：

```yaml
yulinlin:
  sqlite:
    file: data/local.db
```

相对路径基于进程工作目录，不是 classpath。启动时创建父目录与数据库文件，并启用 WAL；默认不创建业务表，开启下文实体扫描后可以自动建表。生产环境建议使用持久化目录的绝对路径。未配置 `file` 时使用 data/local.db，不再以 file 是否存在决定启用；显式配置空路径仍会校验失败。只接受文件路径，不接受 JDBC URL、内存数据库或 `file:` URI。

默认会话组是 `sqlite`，请求时使用 `newInstance("sqlite", ...)`。只有 sqlite 一个会话组时可以省略 group；多个组并存时显式选择，或设置 yulinlin.datasource.default-group=sqlite；旧 primary 组也需显式配置为默认，不能仅凭组名自动优先。旧业务需要 local 组时可显式设置 yulinlin.sqlite.group=local。无需为了省略参数把 SQLite 组改成 primary。实体映射和 CRUD 按 ORM 专题使用；将 MySQL 建表语句换成 SQLite DDL，不要照搬 `ENGINE`、`AUTO_INCREMENT` 等 MySQL 专用语法。

## CRUD 完全沿用现有用法

### 可选：扫描实体自动建表

```yaml
yulinlin:
  sqlite:
    file: data/local.db
    schema:
      enabled: true
      packages:
        - com.example.local.entity
```

自动扫描指定包及子包中带 `@JoinTable("表名")` 的具体实体，在 SQLite 会话创建前完成建表。不实例化实体，不注册成 Spring Bean；仅操作模块内置的 SQLite 连接池，不操作 MySQL。默认关闭；开启却未指定包、没有扫描到物理表实体时启动失败，避免配置错误被忽略。

复用现有继承字段、`@JoinField(name=...)`、`@JoinMeta(primaryKey=true)` 和 JDBC 驼峰列名配置。跳过静态/transient 字段、`exist=false`、计算字段以及 JOIN 查询模型。建议扫描专用实体包，不要混入查询投影 DTO。表名必须是普通名称，不支持别名、SQL 表达式或限定名称；不自动创建索引、外键或复合主键。主键值继续按原框架方式提供，不新增生成主键回填机制。

| 实体字段类型 | 自动建表列类型 |
| --- | --- |
| byte/short/int/long 及包装类、boolean/Boolean | `INTEGER` |
| float/double 及包装类 | `REAL` |
| 日期时间、枚举、BigDecimal、BigInteger | `TEXT` |
| String、字符、JSON 对象、Map、集合 | `TEXT` |
| byte[] | `TEXT`，现有 ByteArrayCoder 输出 Base64 |

编解码继续使用现有 SQL 编解码器，不新增转换层。自定义编码器必须与上述存储约定兼容；建表不保证任何任意 Java 类型都可以被现有编码器正确读写。

`BigDecimal` 存 TEXT 可保留现有字符串编码精度，但数据库直接排序、范围比较是文本语义，**不等于数值大小比较**。日期统一 `yyyy-MM-dd HH:mm:ss` 且时区一致时可做文本范围查询；当前 DateCoder 只有秒精度。

也可以注入管理器手动执行（方法体片段）：

```java
// 注入 com.yulinlin.jdbc.sqlite.SqliteSchemaManager schemaManager
var result = schemaManager.scanAndCreate("com.example.local.entity");
// 或只处理明确指定的实体：
schemaManager.createTables(SysUserVo.class, LocalConfig.class);
// result.entities() / result.created() / result.existing()
```

手动入口即使自动扫描关闭也可使用；应在业务事务外、没有并发迁移时调用。每批 DDL 使用一个事务，失败全部回滚。不存在的表创建，已有表检查列类型亲和性和主键；缺列、类型不兼容或同表映射冲突会报错，不自动补列、删表或改数据。额外普通列允许保留，但额外列自身的 NOT NULL、CHECK、触发器等约束仍由数据库执行，本工具不是完整迁移/约束验证器。

以下是方法体片段，假设 `SysUserVo` 是现有框架实体、对应业务表已创建：

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;

// SQLite 默认会话组为 sqlite。
var users = ModelSelectWrapper.newInstance("sqlite", SysUserVo.class).selectList();

// 批量插入：一次传入集合，内部使用事务和 JDBC batch。
ModelInsertWrapper.newInstance("sqlite", usersToInsert).execute();
```

条件、排序、分页、更新和删除继续使用原 Wrapper，不要自行拼接用户输入。批量插入要使用待插入的新数据，不要将查询结果直接重复插入。框架不自动阻止无条件更新或删除。

## 与 MySQL 同时使用

保留 mysql 模块与原 `spring.datasource`，SQLite 默认使用独立的 `sqlite` 组，以下 `group: sqlite` 可省略：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/app
    username: app
    password: ${DB_PASSWORD}
yulinlin:
  sqlite:
    file: data/local.db
    group: sqlite
```

```java
// 第一个参数就是数据源会话组，与 oss 的选择方式相同。
var localUsers = ModelSelectWrapper.newInstance("sqlite", SysUserVo.class).selectList();
var mysqlUsers = ModelSelectWrapper.newInstance("mysql", SysUserVo.class).selectList();
```

`spring.datasource.hikari` 仍用于主库。SQLite 使用模块内置的连接池，不继承 MySQL URL 或连接池设置，也不注册 `DataSource` Bean。连接池由 `SqliteDatabase` 创建并在应用关闭时释放；业务无需声明或注入 SQLite DataSource。自定义主 DataSource Bean 时，多主库按 Spring 的规则选择 `@Primary`；SQLite 组保持 `sqlite` 即可，不会参与 DataSource Bean 的选择。不要将不同数据库注册在同一个组下做随机路由。

## 默认值与性能取舍

| 配置/行为 | 默认值 | 含义 |
| --- | --- | --- |
| `file` | `data/local.db` | 本地文件路径，相对路径基于进程工作目录 |
| `group` | `sqlite` | Wrapper 第一个参数指定的会话组 |
| `busy-timeout` | `5000` | 等待 SQLite 锁的毫秒数，不是查询超时 |
| `synchronous` | `NORMAL` | 可选 `FULL`；不提供关闭同步的默认方案 |
| 日志模式 | WAL | 启动时启用并验证，失败则启动失败 |
| 外键 | ON | 每个物理连接启用外键检查 |
| 连接池 | 1 个连接 | 本模块池内操作串行，减少写连接争抢 |

```yaml
yulinlin:
  sqlite:
    file: data/local.db
    busy-timeout: 5000
    synchronous: FULL
```

WAL 不代表多个写事务可以同时执行。单连接方案偏向简单可靠的本地写入，不是最大化读并发；长事务会占用唯一连接。连接池等待上限为 `max(1000, busy-timeout + 1000)` 毫秒，超时抛异常，不会无限等待。

`NORMAL` 是性能与持久性的折中，断电或操作系统崩溃时最近已提交的数据可能丢失；更重视持久性使用 `FULL`。批量写入优先一次提交集合，不要每行单独开事务。没有针对你的业务负载跑性能基准，因此不承诺吞吐量。

## 事务

不写事务注解也能执行 CRUD；这不等于禁用 SQLite 事务。单次 ORM 请求内部提交，批量失败回滚，多个独立调用不会自动合并成一个业务事务。

SQLite 直接参与框架的会话事务，不自动注册 `sqliteTransactionManager`，也无需业务注册。框架会记录事务中使用的会话，在正常结束时逐个提交、异常时逐个回滚。

```java
// 放在 Spring 管理的 Service public 方法上，通过代理调用。
@com.yulinlin.data.core.anno.JoinTransaction
public void saveLocal() {
    ModelInsertWrapper.newInstance("sqlite", usersToInsert).execute();
    // 同一事务内继续执行其他框架 ORM 操作
}
```

也可以使用框架已兼容的 Spring `@Transactional` 注解，或直接调用 `SessionUtil.route().transaction(() -> { ... })`。仅有 SQLite 时，不需要为此额外创建 Spring JDBC 事务管理器，注解由框架事务切面管理会话。与 MySQL 共存时，Spring 自身的事务拦截器可能仍管理主库；跨框架会话的事务边界推荐使用 `@JoinTransaction`。

SQLite 直接使用通用 `JdbcSession` 和 `JdbcSessionFactory`，不另建 SqliteSession 子类；只配置 SQLite 解析器和单连接上限。各 JdbcSession 都保持自己的连接与事务状态，SQLite 的 `.batch()` 仍在同一连接同步执行。多数据源事务逐个提交，并非分布式原子提交；中途提交失败不能保证其他已经提交的数据回滚。`REQUIRES_NEW`、保存点、`noRollbackFor` 等 Spring 高级事务属性不属于框架切面的完整语义。

SQLite 的 `session.supportsParallelWrites()` 返回 false：整批请求不做并发分组，不投递写入工作线程，减少 SQLite 单一写入者限制下的锁竞争。底层仍默认每 256 条执行一次 JDBC batch，并复用同一个连接和同一 SQL 模板的 PreparedStatement；这不是每 256 条 commit。可通过 `yulinlin.datasource.jdbc.execute-batch-size` 调整批次大小，不改变事务边界；`parallel-connections` 的通用配置不覆盖 SQLite 的单连接限制。

## 方言与运维边界

- 普通 CRUD、条件、排序和分页复用 JDBC 通用解析；MySQL 原解析器类名保留兼容入口。
- SQLite 不支持 `SELECT FOR UPDATE`，底层 `SelectWrapper.lock()` 解析时明确报错，不静默忽略锁；这不是 ModelSelectWrapper 的方法。
- MySQL 的日期/时间间隔分组不直接复用；SQLite 当前对此明确报不支持。自定义 SQL、函数、JSON 和复杂 JOIN 的语义需要按 SQLite 验证，不保证所有 MySQL SQL 等价。
- WAL 文件需要可写的本地目录，不要把数据库放在网络共享盘。运行期间不要单独删除 `-wal`、`-shm` 或只复制主文件作为可靠备份。
- JDK 25 的 SQLite 原生库可能提示 native-access 警告，启动时可添加 `--enable-native-access=ALL-UNNAMED`。

## 验证范围

集成测试位置：`sqlite/src/test/java/com/yulinlin/jdbc/sqlite/SqliteIntegrationTest.java`。覆盖真实临时文件、WAL/同步/外键设置、CRUD、分页、批量失败回滚、Spring 注解的框架回滚、多会话提交与回滚、重开文件，以及 MySQL/SQLite 独立会话路由。共存测试不连接外部 MySQL，不等于 MySQL 服务器回归测试。

```shell
mvn -pl sqlite -am -Dtest=JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```
