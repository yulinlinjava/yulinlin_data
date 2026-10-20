# 关联查询

[文档首页](../README.md) / [关联与事务](README.md)

JoinQuery 级联、动态值、固定值和复杂查询条件。

## 本页导航

- [行为速查](#行为速查)
- [用户角色菜单级联](#用户角色菜单级联)
- [动态值和固定值](#动态值和固定值)
- [复杂查询条件](#复杂查询条件)

## 行为速查

| 配置/注解 | 加载 | 修改 |
| --- | --- | --- |
| JoinQuery | 查询结果增强时立即查询关联 | 普通对象，不因此自动写库 |
| JoinQuery + JoinLazy | 框架业务事务内首次访问 getter 时加载 | 仅延迟读取；关联选库仍需要路由 |
| 查询 `.autoUpdate()` | 主对象与关联对象使用同一层代理 | setter 的非 null 修改在提交前写回 |
| 方法/类 `@JoinSync` | 作用域内所有查询等同 `.autoUpdate()` | 必须同时存在框架业务事务 |
| `yulinlin.data.auto-update=true` | 全局查询默认自动更新 | 可由单次 `.autoUpdate(false)` 覆盖 |

ORM 查询得到的模型统一由 EntityProxyFactory 增强。一个实体最多只有一层代理；同一拦截器处理 JoinLazy getter 与持久化 setter，不再叠加 LazyProxy/SyncProxy。自己 new 出来的 DTO 仍需调用 RouteSession 的兼容代理入口。

## 用户角色菜单级联

以下完整 DTO 放在 demo.dto.RouterDetails。引用的三个业务实体必须由应用提供，不依赖 admin 模块；每个目标实体需有表映射、无参构造、getter/setter 和明确类型的 ID。

| 实体 | 示例契约 |
| --- | --- |
| demo.domain.SysUserEntity | String id、username、nickname；List<String> sysRoleIds |
| demo.domain.SysRoleEntity | String id；List<String> sysMenuIds |
| demo.domain.SysMenuEntity | String id；菜单显示属性 |

sysRoleIds/sysMenuIds 的元素类型必须与目标 id 一致，字段与表中 JSON/集合存储匹配。Lombok Data 需要业务项目配置注解处理。

```java
package demo.dto;

import com.yulinlin.data.core.anno.JoinQuery;
import demo.domain.SysUserEntity;
import demo.domain.SysRoleEntity;
import demo.domain.SysMenuEntity;
import lombok.Data;
import java.util.List;

@Data
public class RouterDetails {
    private String username;
    private String loginType;

    @JoinQuery(primary = "username", value = "${username}")
    private SysUserEntity user;

    @JoinQuery(primary = "id", value = "${user.sysRoleIds}")
    private List<SysRoleEntity> roles;

    @JoinQuery(primary = "id", value = "${roles.sysMenuIds}")
    private List<SysMenuEntity> menus;

    public RouterDetails() { }
    public RouterDetails(String username, String loginType) {
        this.username = username;
        this.loginType = loginType;
    }
}
```

业务方法体片段，username/loginType 已由调用方提供：

```java
import com.yulinlin.data.core.session.SessionUtil;
import demo.dto.RouterDetails;

RouterDetails details = SessionUtil.callable("mysql", () ->
        SessionUtil.route().getLazyProxy(new RouterDetails(username, loginType)));
```

这个类没有 JoinLazy，入口会立即尝试加载 user、roles、menus。getLazyProxy 的名称不代表所有字段都延迟。

即时关联按反射字段顺序处理，不是依赖拓扑排序。示例按 user → roles → menus 放置，但复杂图不能仅靠反射顺序保证正确，必要时分步查询或采用下面的懒字段方案。路由的默认递归深度保护是 6，不等于循环关联可以安全展开。

### 动态值和固定值

| JoinQuery.value | 含义 |
| --- | --- |
| "${username}" | 当前对象 username |
| "${user.sysRoleIds}" | 当前对象 user 的角色 ID |
| "${roles.sysMenuIds}" | 遍历 roles 汇集菜单 ID |
| "username" | 固定字符串，不是当前属性值 |

动态取值必须带 ${...}，不能按其他 ORM 的属性名规则猜测。primary 是目标匹配属性，默认 id，不要求一定是物理主键。

放在数据库实体里的关系属性加 `@JoinField(exist = false)`，避免被当作列。纯组装 DTO 可以没有 JoinTable，但目标实体仍需完整映射和实际表。

List/Set 的元素类型必须可推断，不使用原始 List、List<?> 或不明确泛型。无匹配时单对象为 null，集合为空；单对象匹配键应保证唯一，否则取第一条。

### 复杂查询条件

`wheres` 用于根据当前父对象的多个属性构造关联查询。下面的对象会查询指定视频下、状态可见、创建时间较新且正文包含关键词的前 20 条评论：

```java
package demo.dto;

import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinOrder;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinWhere;
import demo.domain.CommentEntity;
import lombok.Data;

import java.util.List;

@Data
public class VideoCommentDetails {
    private String videoId;
    private List<Integer> visibleStatuses;
    private String keyword;
    private String createdAfter;
    private String createdBefore;

    @JoinQuery(
        wheres = {
            @JoinWhere(name = "videoId", value = "${videoId}"),
            @JoinWhere(
                name = "status",
                value = "${visibleStatuses}",
                condition = ConditionEnum.in
            ),
            @JoinWhere(
                name = "content",
                value = "${keyword}",
                condition = ConditionEnum.like
            ),
            @JoinWhere(
                name = "crtTime",
                value = "${createdAfter}",
                condition = ConditionEnum.gte
            ),
            @JoinWhere(
                name = "crtTime",
                value = "${createdBefore}",
                condition = ConditionEnum.lte
            )
        },
        order = @JoinOrder(name = "crtTime", asc = false),
        size = 20
    )
    private List<CommentEntity> comments;
}
```

创建条件对象并执行关联增强：

```java
VideoCommentDetails details = new VideoCommentDetails();
details.setVideoId("video-1001");
details.setVisibleStatuses(List.of(1, 2));
details.setKeyword("Java");
details.setCreatedAfter("2026-10-01 00:00:00");
details.setCreatedBefore("2026-11-01 00:00:00");

VideoCommentDetails result = SessionUtil.callable("mysql", () ->
        SessionUtil.route().getLazyProxy(details));

List<CommentEntity> comments = result.getComments();
```

该示例等价于以下逻辑条件：

```sql
video_id = ?
AND status IN (?, ?)
AND content LIKE ?
AND crt_time >= ?
AND crt_time <= ?
ORDER BY crt_time DESC
LIMIT 20
```

`name` 写目标实体的 Java 属性名，字段到数据库列名的转换仍由 `@JoinField` 和具体数据源解析器完成。`value = "${...}"` 从当前父对象取值；不带 `${}` 的值会作为固定字符串，例如 `@JoinWhere(name = "type", value = "PUBLIC")`。

值为 `null`、空字符串或空集合的条件会被忽略；如果所有动态条件都没有值，框架返回空关联结果，不会退化成无条件全表查询。`ConditionEnum.in` 的值必须是集合或数组属性，其元素会展开并去重。

当前 `wheres` 中的条件统一按 AND 使用，并且每个父对象单独构造查询。因此列表场景可能产生 N+1，不享受普通 `primary/value` 分支的批量 IN 分配。`JoinWhere.and()` 当前没有接入代理查询；`between` 也不应在 `wheres` 中使用，需要范围条件时分别写 `gte` 和 `lte` 两个父对象属性。

关联计数使用 `model` 指定被统计的实体，字段使用 `Long`：

```java
@JoinQuery(
    model = CommentEntity.class,
    wheres = {
        @JoinWhere(name = "videoId", value = "${videoId}"),
        @JoinWhere(name = "status", value = "${visibleStatuses}", condition = ConditionEnum.in)
    }
)
private Long visibleCommentCount;
```

这里的 `model` 会进入 `count()` 分支，不是用来替代 `List<CommentEntity>` 泛型声明的通用参数。

---

下一步：返回[关联与事务主题目录](README.md)按需选择相关专题。
