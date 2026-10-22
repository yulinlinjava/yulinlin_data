# 公共实体与页面参数

[文档首页](../README.md) / [项目实践](README.md)

PagePo、JoinWhere、R、IdEntity、SuperEntity 和 TreeEntity。

## 本页导航

- [公共业务模型](#公共业务模型)
- [页面查询对象：`PagePo + @JoinWhere`](#页面查询对象pagepo--joinwhere)
- [统一 Controller 响应：`R<E>`](#统一-controller-响应r)
- [基础实体选择](#基础实体选择)
- [`IdEntity`](#identity)
- [`SuperEntity`](#superentity)
- [`TreeEntity`](#treeentity)
- [自定义项目基类](#自定义项目基类)

## 公共业务模型

框架把常见的页面查询、分页结果和 Web 响应模型集中提供给业务项目，避免每个模块重复声明 `pageNumber`、`pageSize`、排序、ID 和列表等字段。它们是对外公共 API，不是 ORM 内部临时对象。

| 类型 | 所属模块 | 用途 |
| --- | --- | --- |
| `PagePo` | `common` | 页面查询入参基类，统一分页、排序和运行时追加条件 |
| `OrderItem` | `common` | 一个排序字段及升降序 |
| `PageVo<E>` | `common` | 兼容已有业务的分页响应类型，继承 `Page<E>` |
| `Page<E>` | `lang` | ORM 和 Service 共用的分页结果，包含当前页列表、总数和扩展值 |
| `IdPo` | `starter` | Web 接口的单个字符串 ID 入参 |
| `ListPo<E>` | `starter` | Web 接口的通用列表入参 |
| `R<E>` / `ResponseVo<E>` | `starter` | 标准 Web 响应体；新代码使用 `R`，`ResponseVo`仅保留废弃兼容写法 |

这些类型按使用的门面模块引入。只使用 ORM 的项目可直接使用 `PagePo`和 `Page`，不必为了 `IdPo`或 `R`引入 Web Controller。它们也不要求实现 Java `Serializable`，HTTP JSON 仍由 Spring/Jackson 处理。

### 页面查询对象：`PagePo + @JoinWhere`

页面列表查询推荐为每个业务页面定义一个很薄的 `XxxPo extends PagePo`。父类统一携带分页和排序，子类只声明本页面的业务条件：

```java
package com.example.app.pay.api;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import com.yulinlin.data.lang.util.DateTime;
import lombok.Data;

import java.util.List;

@Data
public class PayChannelPo extends PagePo {

    @JoinWhere(condition = ConditionEnum.eq)
    private String platformId;

    @JoinWhere(condition = ConditionEnum.eq)
    private String status;

    @JoinWhere(condition = ConditionEnum.between)
    private List<DateTime> crtTime;
}
```

Controller 只接收业务查询对象，Service 复用 `ISuperService.page(PagePo)`：

```java
@PostMapping("/page")
public R<Page<PayChannelEntity>> page(@RequestBody PayChannelPo po) {
    return R.newInstance(payChannelService.page(po));
}
```

请求可以同时携带业务条件、分页和排序：

```json
{
  "pageNumber": 1,
  "pageSize": 20,
  "platformId": "web",
  "status": "enabled",
  "crtTime": ["2026-10-01 00:00:00", "2026-10-15 23:59:59"],
  "orderList": [
    { "name": "crtTime", "asc": false }
  ]
}
```

`ISuperService.page(po)`的核心过程是先把查询对象交给 Wrapper，再应用分页和排序（接口实现还会合并 `PagePo.and/or`中的运行时条件）：

```java
Page<PayChannelEntity> result = ModelSelectWrapper
        .newInstance(PayChannelEntity.class, po)
        .page(po.getPageNumber(), po.getPageSize())
        .orderBy(order -> {
            if (po.getOrderList() != null) {
                for (OrderItem item : po.getOrderList()) {
                    order.orderBy(item.getName(), item.isAsc());
                }
            }
        })
        .selectPage();
```

查询对象解析遵循以下规则：

- 只有带 `@JoinWhere` 且实际提供值的子类字段生成 WHERE；`pageNumber`、`pageSize`和 `orderList`没有该注解，不会成为查询条件。
- `@JoinWhere.name`省略时使用查询对象的 Java 属性名；查询属性与实体属性不同时显式填写实体属性名。
- `between`传入按“起始、结束”排列的两个值，`in`传集合；字段类型继续使用框架现有 SQL 编解码规则。
- `PagePo.and(...)`和 `or(...)`用于运行时才知道字段或操作符的补充条件，不是普通页面查询的必写步骤。
- `orderList.name`应使用实体 Java 属性名。直接接收前端排序时，业务层应限制允许排序的属性，避免任意字段参与查询。
- 复杂嵌套 AND/OR、子查询或数据库专用表达式继续使用 Wrapper，不要把所有 SQL 逻辑塞进 PO。

不通过 `ISuperService`时，也可以直接把同一个查询对象传给指定数据源：

```java
Page<PayChannelEntity> result = ModelSelectWrapper
        .newInstance("mysql", PayChannelEntity.class, po)
        .page(po.getPageNumber(), po.getPageSize())
        .selectPage();
```

`PayChannelEntity.class`决定表、返回字段和结果类型，`PayChannelPo`只描述 WHERE、分页和排序输入。查询 PO 不需要 `@JoinTable`，也不应开启自动 Schema。`@JoinWhere`支持的操作符、空值和 AND/OR 精确语义见[查询条件对象](../orm/query.md#查询条件对象)。

### 统一 Controller 响应：`R<E>`

普通 JSON Controller 统一使用 `@PostMapping + @RequestBody`并显式返回 `R<E>`。查询、详情、创建、修改和删除都把参数放进一个 JSON 请求体；配置设备密钥或自定义 CryptoCodec 后，无注解接口也使用全局加解密规则，`@ApiCrypto(false)` 用于兼容明密文请求、保持响应明文。框架不会自动把任意返回对象包装为 R。

```java
@PostMapping("/page")
public R<Page<PayChannelEntity>> page(@RequestBody PayChannelPo po) {
    return R.ok(payChannelService.page(po));
}

@PostMapping("/delete")
public R<Void> delete(@RequestBody IdPo po) {
    payChannelService.delete(po.getId());
    return R.ok();
}
```

标准工厂方法：

| 方法 | 结果 |
| --- | --- |
| `R.ok()` | 成功且没有响应数据 |
| `R.ok(data)` | 成功并返回数据 |
| `R.ok(data, message)` | 成功并返回数据和提示 |
| `R.fail(message)` | 默认 `500`失败响应 |
| `R.fail(code, message)` | 指定错误码的失败响应 |

成功响应默认 `code=200`、`ok=true`；失败工厂始终设置 `ok=false`。每个新响应都会写入当前 `timestamp`，`crypt`默认 false。旧的 `R.newInstance(...)`和 `ResponseVo<E>`暂时保留二进制/源码兼容，但均已废弃，新业务不要继续使用。

`starter`默认注册最低优先级的异常处理器：参数绑定或 JSON 格式错误返回 `400`；`ApiException`携带 `400–599`标准 HTTP 错误码时使用该状态，其他数值按 `400`处理；`ResponseStatusException`保留 HTTP 状态；未识别异常记录完整服务端日志并返回 `500 + 系统繁忙，请稍后重试`。错误 HTTP 状态和 `R.code`保持一致；业务项目可以提供更高优先级的 `@RestControllerAdvice`覆盖细分规则。不要把 SQL、文件路径、密钥或异常堆栈写进客户端 `msg`。

接口按全局规则需要加密且返回 `R`时，只替换并加密 `data`，随后设置 `crypt=true`；`code`、`ok`、`msg`和 `timestamp`保持明文。`data == null`时跳过加密并保持 `crypt=false`，错误提示因此无需客户端先解密。详细协议见[接口安全](../security/README.md)。

业务 JSON 接口不要用 `@GetMapping`、`@PutMapping`、`@PatchMapping`或 `@DeleteMapping`承载加密参数，也不要把敏感条件放在 query/path 中。监控页面、静态资源、健康检查、文件下载、图片/视频/音频流、SSE、重定向以及直接写 `HttpServletResponse`的接口不受这条约束；流式响应不使用 `R`包装，应配置 `crypto.exclude-paths` 跳过 JSON 加解密流程，不能仅靠不写注解规避。

## 基础实体选择

`common` 模块提供了几个可复用实体。它们不只是字段集合，还实现了 `AbstractModel` 的 CRUD 便捷入口和写入前生命周期，因此应按表结构选择，不要为了少写字段而继承。

| 类型 | 持久化字段 | 自动行为 | 适用场景 |
| --- | --- | --- | --- |
| 普通 POJO | 自行定义 | 无 | 数据库自增主键、`Long`/UUID 主键、投影结果或非标准表 |
| `IdEntity<E>` | `String id` | 插入前在 `id` 为 null 或空串时生成 Snowflake 字符串 ID | 大多数由应用生成字符串主键的业务表 |
| `SuperEntity<E>` | `id`、`crtTime`、`uptTime` | 包含 `IdEntity` 行为，插入时补齐空的创建/更新时间 | 同时具有主键和审计时间列的表 |
| `TreeEntity<E>` | `id`、`crtTime`、`uptTime`、`parentId`、`parentPath` | 默认根节点 ID 为 `"0"`，`selectTree()` 可按 `ITreeNode` 组装 `children` | 菜单、部门和分类等树表 |
| `EntityCounter` | 不持有表字段 | 按主键立即/聚合自增更新；或按非数字维度聚合数字字段后批量插入统计记录 | 播放量、点击量、热度和分渠道统计；它是写入门面，不作为实体父类 |

### `IdEntity`

```java
@JoinTable(value = "sys_user", autoSchema = true)
public class UserEntity extends IdEntity<UserEntity> {
    @JoinField(textLength = 64, description = "登录用户名")
    private String username;

    public UserEntity() { }
    // getter/setter
}
```

`id` 已声明为主键和默认条件字段，子类不要再次定义同名字段。插入前的 `insertBefore()` 会保留业务已设置的 ID，仅在未设置时调用 `generateId()`。需要其他应用侧 ID 策略时可在子类重写 `generateId()`；如果主键由数据库自增、序列或触发器产生，应改用普通 POJO 并显式定义主键。

`nextIncrId()` 会用表中当前最大 ID 初始化进程内计数器，之后在本进程递增。它不是数据库自增 ID，也不保证多实例唯一；分布式业务保持默认 Snowflake 策略或接入统一 ID 服务。

`IncrementEntity` 的动态表名/列名方式仅为旧代码保留，已经废弃。已有视频记录使用 `EntityCounter.update(VideoEntity.class, videoId).inc(VideoEntity::getViewCount).submit()`；统计明细使用 `EntityCounter.insert(stat).submit()`，非数字字段作为维度、数字字段分别累加，默认一分钟批量插入。不要提前填充统计实体的 ID/时间，以免它们意外成为不同维度。立即写入、类型范围、聚合规则、配置和数据丢失边界见[实体计数器](../write-sync/counter.md#实体计数器)。

多字段增量也可以新建模型，仅设置主键和本次增量后调用 `EntityCounter.update("mysql", delta).submit()`，或 `EntityCounter.update(delta).execute()` 立即写入。数字正值增加、负值减少，非数字字段忽略，主键只作条件。不要传入查回的完整实体：它不是覆盖更新，已有的非零数字都会再次累加。模型更新不触发 `updateBefore`、自动更新时间或版本校验，完整用例见 [通过模型提交更新增量](../write-sync/counter.md#通过模型提交更新增量)。

### `SuperEntity`

```java
@JoinTable(value = "video_comment", autoSchema = true)
public class CommentEntity extends SuperEntity<CommentEntity> {
    @JoinField(description = "视频 ID")
    private String videoId;

    @JoinField(textType = TextTypeEnum.text, description = "评论内容")
    private String content;

    public CommentEntity() { }
    // getter/setter
}
```

`crtTime` 和 `uptTime` 的 Java 类型都是框架 `DateTime`，按驼峰转下划线规则对应 `crt_time` 和 `upt_time`。只有物理表确实具有这两列时才继承 `SuperEntity`；自动 Schema 也会把继承字段当作真实列处理，并在 MySQL、PostgreSQL、H2 中创建原生时间列，在 SQLite 中创建固定格式 TEXT 列。

当前 `updateBefore()` 只在 `uptTime == null` 时填入当前时间。如果业务要求每次更新都刷新时间，应在写入前显式 `setUptTime(DateTime.now())`，或在项目自定义父类中重写 `updateBefore()`，不要把“非空时保留原值”误解为无条件自动刷新。

### `TreeEntity`

```java
@JoinTable(value = "sys_menu", autoSchema = true)
public class MenuEntity extends TreeEntity<MenuEntity> {
    @JoinField(textLength = 64, description = "菜单名称")
    private String name;

    public MenuEntity() { }
    // getter/setter
}
```

`children` 已标记 `@JoinField(exist = false)`，只用于内存树结构，不会成为表列。`parentId` 和 `parentPath` 是真实列；新节点没有指定父节点时使用全局根值 `"0"`。如果项目需要其他根值，应在启动配置阶段统一设置，不要在请求中反复修改这个静态值。

`ModelSelectWrapper.selectTree()` 会将查到的 `ITreeNode` 列表在内存中组装为树；普通 `selectList()` 仍返回扁平列表。`findChildren()` 和 `findParent()` 是使用默认 group 的便捷方法；在多数据源项目中，优先在 Service/Repository 明确路由后查询，避免树节点自身隐式选库。

### 自定义项目基类

只有多张表真正共享同一列语义时才增加项目基类，例如所有业务表都具有租户 ID：

```java
public abstract class TenantEntity<E extends TenantEntity<E>> extends SuperEntity<E> {
    @JoinField(textLength = 64, description = "租户 ID")
    private String tenantId;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

实体的泛型参数始终传具体类，例如 `UserEntity extends IdEntity<UserEntity>`。不要在子类重复声明 `id`、`crtTime`、`uptTime`、`parentId` 或 `parentPath`；重名字段会让反射映射、Schema 比对和更新条件变得不确定。与表无关的计算属性仍必须显式使用 `@JoinField(exist = false)`。

---

下一步：返回[项目实践主题目录](README.md)按需选择相关专题。
