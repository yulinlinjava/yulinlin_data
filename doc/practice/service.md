# 通用 Service 与树服务

[文档首页](../README.md) / [项目实践](README.md)

复用 ISuperService/ITreeService，区分 Repository 职责。

## 本页导航

- [通用 Service 接口](#通用-service-接口)
- [`ISuperService` 用法](#isuperservice-用法)
- [`ITreeService` 用法](#itreeservice-用法)
- [与 Repository 如何选择](#与-repository-如何选择)

## 通用 Service 接口

`common` 模块通过接口默认方法提供无状态 CRUD 和树服务能力。新项目直接 `implements ISuperService<E>` 或 `implements ITreeService<E>`，不再占用业务 Service 的类继承位。`impl.ServiceImpl/TreeImpl` 与 `impl2` 只作为已废弃兼容层。

| 接口/实现 | 用途 | 关键能力 |
| --- | --- | --- |
| `ISuperService<E>` | 通用 CRUD 契约和默认实现 | 按 ID 查询、新增、更新、删除、保存、分页 |
| `ServiceImpl<E>` | 已废弃兼容类 | 保留旧 `extends ServiceImpl` 写法和 `dataGroup()` 扩展点 |
| `ITreeService<E>` | 树业务契约和默认实现 | 维护父路径、移动子树、删除后代、内存组树 |
| `TreeImpl<E>` | 已废弃兼容类 | 保留旧 `extends TreeImpl` 写法 |

### `ISuperService` 用法

```java
package com.example.app.user.service;

import com.example.app.user.domain.UserEntity;
import com.yulinlin.common.service.ISuperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCrudService implements ISuperService<UserEntity> {

    @Transactional(readOnly = true)
    public UserEntity detail(String id) {
        return findById(id);
    }

    @Transactional
    public int create(UserEntity user) {
        return insertOne(user);
    }

    @Transactional
    public int change(UserEntity user) {
        return updateOne(user);
    }
}
```

Service 接口默认方法不会注册 Spring Bean，也不会自动开启事务。具体实现类仍必须使用 `@Service`，并由业务入口定义事务边界。实现这个接口不等于自动获得事务、缓存或数据源路由。

常用方法语义：

| 方法 | 行为 |
| --- | --- |
| `findById(id)` / `findByIdIn(ids)` | 按实体主键查询 |
| `all()` / `page(PagePo)` | 查询全部或动态分页 |
| `insertOne/list` | 明确新增，会执行实体 `insertBefore()` |
| `updateOne/list` | 明确按主键更新，会执行 `updateBefore()` |
| `deleteById/IdIn` | 明确按主键删除 |
| `save/saveList` | 先查询主键是否存在，再将对象分为 insert/update 两组 |
| `newInstance()` | 通过反射创建实体实例 |

`save()` 不是数据库原生 UPSERT：它会先查后写，有额外往返，且并发时仍要依赖唯一索引处理竞态。业务明确知道是新增还是更新时，直接调用 `insert*` 或 `update*` 性能更好，语义也更清楚。

`page(PagePo)`会解析业务子类上的 `@JoinWhere`字段，再应用 `PagePo`自带的动态条件、分页和排序；完整页面写法见[公共业务模型](models.md#页面查询对象pagepo--joinwhere)。不要把前端传入的排序字段名无限制透传；Controller/Service 应使用允许列表转换为可排序的实体属性。

### `ITreeService` 用法

```java
package com.example.app.menu.service;

import com.example.app.menu.domain.MenuEntity;
import com.yulinlin.common.service.ITreeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuService implements ITreeService<MenuEntity> {

    @Transactional(readOnly = true)
    public List<MenuEntity> tree(MenuQuery query) {
        return getTree(query);
    }

    @Transactional
    public int saveMenu(MenuEntity menu) {
        return save(menu);
    }

    @Transactional
    public int removeMenu(String id) {
        return deleteById(id);
    }
}
```

`ITreeService` 继承全部通用 CRUD，并增加以下约定：

1. `save()` 会根据 `parentPath` 最后一项推导 `parentId`，空路径表示根节点。
2. `updateOne()` 在父节点变化时重新计算后代节点的 `parentPath`。
3. `deleteByIdIn()` 会同时删除 `parentPath` 匹配的后代节点，这是级联删除，调用前必须确认业务意图。
4. `getTree(query)` 先查询扁平数据，再在内存中组树；实体实现 `ISortValue` 时按 `sortValue` 递归排序。

树移动和级联删除会修改多行，必须在事务中执行。接口会拒绝 `parentPath` 包含自身 ID 的移动，防止节点移入自己的后代形成环。大树的 `getTree()` 会一次性加载数据，后台菜单等小树可直接使用；数万节点的树应改用按层/按父 ID 分页查询。

### 与 Repository 如何选择

- 简单后台 CRUD、树维护，且始终使用默认数据源时，可实现 `ISuperService` 或 `ITreeService`。
- 需要声明式查询、方法级 `@JoinCache`、`@JoinSession` 或希望数据访问边界更清晰时，优先 Repository + 业务 Service。
- 一个业务域选择一个主入口。不要在同一个 Service 中既实现通用接口、又为同一批 CRUD 注入 Repository，造成两套规则和缓存策略。
- 这两个接口默认方法创建的 Wrapper 没有显式 group，依赖全局默认数据源。多数据源业务优先使用可明确声明 group 的 Repository 或 Wrapper。

---

下一步：返回[项目实践主题目录](README.md)按需选择相关专题。
