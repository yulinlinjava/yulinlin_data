# ORM 接入与 CRUD

多个数据库的配置、`JdbcSessionFactory.create` 注册及选库，见同目录 `15-datasources.md`（单文件指南已包含该专题）。

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`common/.../domain/IdEntity.java`、`SuperEntity.java`；`core/.../model/`；`mysql/.../MysqlParseAutoConfig.java`。若与实际安装版本冲突，以该版本源码为准。

### 3.1 ORM 项目

使用 JDK 25，业务项目采用 Spring Boot 3.5 系列依赖管理：

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

不要假设这些制品已发布 Maven Central；应使用团队制品仓库，或先从同一份源码安装相关模块。core/lang 等同组模块应保持同一构建来源。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
yulinlin:
  http:
    timeout: 10s
```

当前 core、starter、mysql 等模块提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。在正常 Boot 自动配置链中无需额外的框架启用注解。MySQL 自动配置依赖 DataSource，并创建会话名 `primary`、Bean 名 `jdbcSession`。仅引入 starter 不会创建 MySQL 数据库会话。

只需反射/JSON 时可依赖 `com.yulinlin:lang:3.0`。只需 HTTP 时可依赖 `com.yulinlin:core:3.0`；但 core 在 Boot 中还包含 ORM 相关自动配置，不是一个专门拆分的纯 HTTP starter。项目已引入 starter 时无需重复声明 core。

### 3.2 最小实体：避免隐式时间字段

下面以 `IdEntity` 为基类，仅继承 String 类型 id 和主键生成逻辑。一个 public 类放在对应的独立 `.java` 文件中。

```java
package demo.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable("ai_demo_user")
public class DemoUser extends IdEntity<DemoUser> {
    @JoinField(name = "user_name")
    @JoinWhere
    private String username;

    @JoinField
    @JoinWhere
    private Integer status;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

匹配上述模型的示例建表 SQL（仅在自己的演示库执行）：

```sql
CREATE TABLE ai_demo_user (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_name VARCHAR(100),
    status INT
);
```

`SuperEntity<T>` 在 `IdEntity<T>` 上增加 `crtTime`、`uptTime` 及插入/更新前填充逻辑；选择它时要让表结构与这些实际映射字段一致，不能直接套用上面的三列表。

注解规则：

- `@JoinTable` 标记表，`@JoinField(name = "...")` 指定列，`@JoinField(exist = false)` 排除字段。
- 不要假设没有 `@JoinField` 的字段一定会被忽略；当前工厂会处理一些未注解字段。非持久化字段应显式排除。
- 自定义主键建议同时标记 `@JoinField`、`@JoinMeta(primaryKey = true)`、`@JoinWhere`，不要仅依赖一个主键注解覆盖所有工厂行为。
- `@JoinWhere` 参与按对象构造条件；显式 `.eq(...)` 等链式条件可直接使用。
- 主键属性名与数据库列名不一致时，必须额外检查删除等工厂的映射行为，本文不承诺所有路径都正确处理重命名主键。

## CRUD 服务示例

```java
package demo.service;

import demo.domain.DemoUser;
import org.springframework.transaction.annotation.Transactional;
import com.yulinlin.data.lang.util.Page;
import org.springframework.stereotype.Service;

@Service
public class DemoUserService {
    public DemoUser findById(String id) {
        requireId(id);
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getId, id).selectOne();
    }

    public Page<DemoUser> page(int pageNumber, int pageSize) {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("Invalid page parameters");
        }
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getStatus, 1)
                .orderByDesc(DemoUser::getId)
                .selectPage(pageNumber, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public String create(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        DemoUser user = new DemoUser();
        user.setUsername(username);
        user.setStatus(1);
        user.createInsertWrapper().execute();
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(String id, int status) {
        requireId(id);
        DemoUser patch = new DemoUser();
        patch.setId(id);
        patch.setStatus(status);
        return patch.createUpdateWrapper().execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteById(String id) {
        requireId(id);
        DemoUser target = new DemoUser();
        target.setId(id);
        return target.createDeleteWrapper().execute();
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
    }
}
```

方法约定：

| API | 语义 |
| --- | --- |
| `createSelectWrapper().selectList()` | 返回 `List<E>` |
| `selectOne()` | 按第 1 页、每页 1 条查询，不是“多条即抛异常” |
| `selectPage(1, 20)` | 页码从 1 开始，返回框架 `Page<E>` |
| `Page.getList()` / `getTotal()` | 数据列表 / 总数；当前 total 是 int |
| `count()` / `exist()` | 计数 / 存在判断 |
| `createInsertWrapper().execute()` | 执行插入；IdEntity 未设置 id 时生成字符串 ID |
| `createUpdateWrapper().execute()` | 根据模型构造更新；当前工厂跳过 null 字段 |
| `createDeleteWrapper().execute()` | 根据模型主键构造删除；业务侧先校验主键 |

条件可用 `eq/ne/gt/gte/lt/lte/like/likeRight/between/in/nin/isNull`。不要把其他库的 `ge/le/notIn` 名称直接搬过来。复杂嵌套条件先确认 `ModelConditionWrapper` 和 `IConditionWrapper` 的实际签名。

将 patch 字段设为 null 不代表会生成 `SET column = NULL`；清空字段需求必须核查底层字段构造器生成的 SQL。不要把整个 HTTP 请求 DTO 不加限制地复制进更新实体，防止越权更新 id 或敏感字段。

事务说明请同时读取 `20-transactions.md`；上面的服务使用 Spring `@Transactional`。多表、级联等历史案例不属于已核验的完整接入示例。
