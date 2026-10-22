# 插入、更新与删除

[文档首页](../README.md) / [ORM 开发](README.md)

完整 CRUD Service，以及写入和查询缓存的交互。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [CRUD 完整服务](#crud-完整服务)
- [新增用法](#新增用法)
- [更新用法](#更新用法)
- [删除用法](#删除用法)
- [写操作与查询缓存](#写操作与查询缓存)

## CRUD 完整服务

下面完整 Service 使用 mysql 组。对应表已存在、容器已初始化；通过 Spring 代理调用带事务的方法：

```java
package demo.service;

import demo.domain.DemoUser;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.data.lang.util.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoUserService {
    public DemoUser findById(String id) {
        requireId(id);
        return ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                .eq("id", id).selectOne();
    }

    public Page<DemoUser> page(int page, int size) {
        if (page < 1 || size < 1 || size > 200) throw new IllegalArgumentException("Invalid page");
        return ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                .eq("status", 1).orderByDesc("id").selectPage(page, size);
    }

    @Transactional
    public String create(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
        DemoUser user = new DemoUser();
        user.setUsername(username);
        user.setStatus(1);
        ModelInsertWrapper.newInstance("mysql", user).execute();
        return user.getId();
    }

    @Transactional
    public int changeStatus(String id, int status) {
        requireId(id);
        DemoUser patch = new DemoUser();
        patch.setId(id);
        patch.setStatus(status);
        return ModelUpdateWrapper.newInstance("mysql", patch).execute();
    }

    @Transactional
    public int delete(String id) {
        requireId(id);
        return ModelDeleteWrapper.newInstance("mysql", DemoUser.class).eq("id", id).execute();
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
    }
}
```

实体也提供 `createSelectWrapper()`、`createInsertWrapper()`、`createUpdateWrapper()`、`createDeleteWrapper()`。不指定组的入口遵循全局默认组选取；多组时不要靠注册顺序。

### 新增用法

以下新增、更新和删除代码均为业务方法体片段，`List` 使用 `java.util.List`，实体沿用上文 DemoUser。

单条新增直接传实体；IdEntity 会在插入前生成 ID，execute 返回数据库报告的影响行数：

```java
DemoUser user = new DemoUser();
user.setUsername("alice");
user.setStatus(1);

int affected = ModelInsertWrapper.newInstance("mysql", user).execute();
String generatedId = user.getId();
```

同一批数据传集合，默认复用 JDBC batch；是否使用多连接由调用方显式决定：

```java
List<DemoUser> users = List.of(firstUser, secondUser);
int affected = ModelInsertWrapper.newInstance("mysql", users).execute();
```

大批量才考虑 `.batch()`；连接数量、提交大小和原子性限制见[批量与多连接写入](../write-sync/batch.md#批量与多连接写入)。

### 更新用法

对象更新适合“按主键修改非 null 字段”。主键生成 WHERE，其他非 null、可更新字段生成 SET：

```java
DemoUser patch = new DemoUser();
patch.setId(id);
patch.setStatus(2);

int affected = ModelUpdateWrapper.newInstance("mysql", patch).execute();
if (affected != 1) throw new IllegalStateException("User does not exist: " + id);
```

需要明确控制 SET 和 WHERE 时，以实体类型创建 Wrapper：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .field("status", 2)
        .eq("id", id)
        .execute();
```

数值字段支持原子增减；下面只是语法示例，status 必须确实适合做数值累加：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .inc("status", 1)
        .eq("id", id)
        .execute();

int restored = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .dec("status", 1)
        .eq("id", id)
        .execute();
```

`inc/dec` 的 `Money`、`Coin` 参数会经过 ORM 编码器转换为存储单位：例如 `Money.of(12.5)` 对数据库加减 `1250`，`Coin.of(12.5)` 对数据库加减 `125`，无需业务先调用 `encode()`。普通数字按数据库存储单位处理，例如传入 `1L` 就只加减 `1`，不会根据字段类型自动放大。

普通数字和 `BigDecimal` 保留数字参数，`BigInteger` 精确转换为 `BigDecimal`；`AtomicInteger/AtomicLong/LongAdder/DoubleAdder` 在 SQL 参数生成时取值。其他自定义 `Number` 使用已注册的编码器，编码结果必须是 JDBC 支持的数字，否则在执行 SQL 前报错。此规则针对普通更新 Wrapper，不改变 [EntityCounter](../write-sync/counter.md) 的独立类型限制，也不会自动迁移现有数据库列类型。

集合更新会为每个对象建立更新节点。每个对象都应携带完整非空主键，只设置真正需要修改的字段：

```java
DemoUser first = new DemoUser();
first.setId("user-1");
first.setStatus(1);

DemoUser second = new DemoUser();
second.setId("user-2");
second.setStatus(2);

int affected = ModelUpdateWrapper.newInstance("mysql", List.of(first, second)).execute();
```

普通对象更新、自动更新代理和显式 `.field(name, null)` 都跳过 null；它们不能把数据库列清成 NULL。需要清空字段时使用参数化的[自定义写入](raw-sql.md#自定义写入)。`@JoinField(update = false)` 字段不参与对象更新；version 字段按当前值加入条件并递增，仍应检查影响行数判断并发冲突。

### 删除用法

按实体主键删除时，只设置主键即可：

```java
DemoUser key = new DemoUser();
key.setId(id);
int affected = ModelDeleteWrapper.newInstance("mysql", key).execute();
```

按条件删除可以直接使用属性名或 Lambda 条件：

```java
int affected = ModelDeleteWrapper.newInstance("mysql", DemoUser.class)
        .eq("id", id)
        .execute();
```

按主键集合删除前必须处理空集合：

```java
public int deleteByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) return 0;
    return ModelDeleteWrapper.newInstance("mysql", DemoUser.class)
            .in("id", ids)
            .execute();
}
```

也可以传入只包含主键的实体集合，生成多条删除节点：

```java
DemoUser first = new DemoUser();
first.setId("user-1");
DemoUser second = new DemoUser();
second.setId("user-2");

int affected = ModelDeleteWrapper.newInstance("mysql", List.of(first, second)).execute();
```

框架不默认拦截全表写入。`ModelDeleteWrapper.newInstance("mysql", DemoUser.class).execute()` 没有 WHERE，带 SET 但没有 WHERE 的更新也可能影响整表；业务入口必须先校验主键、集合和条件。execute 返回影响行数，单条更新/删除通常应检查是否为 1。

### 写操作与查询缓存

新增、更新和删除在数据库提交成功后，默认清理本次写入涉及的查询缓存 namespace。全局默认值如下：

```yaml
yulinlin:
  cache:
    clear-on-write: true
```

单次写操作可以覆盖全局配置；`clearCache`只控制 ORM 查询缓存，不会删除通过 `CacheClient` 保存的业务缓存：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", patch)
        .clearCache(false) // 本次写入不清理查询缓存；未调用时继承全局配置
        .execute();
```

写 Wrapper 只提供 `clearCache(boolean)`，不再提供写操作版 `cache(...)`、`invalidate(...)` 或 `invalidateAll()`。一般业务保留默认 `true`；只有明确接受数据在 TTL 内短暂陈旧时才关闭。回滚或执行失败不清理缓存。`clear-on-write` 属于可选缓存 Provider；未引入 `cache-caffeine`、`cache-ehcache` 或 `cache-redis` 时不会启用该配置，写路径也不会解析失效 namespace。详细规则见[查询缓存专题](../cache/invalidation.md#失效和一致性)。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
