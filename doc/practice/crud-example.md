# 完整 CRUD 分层案例

[文档首页](../README.md) / [项目实践](README.md)

实体、Repository、DTO、Service 和 Controller 配套使用。

## 本页导航

- [完整业务案例](#完整业务案例)
- [1. 完整实体](#1-完整实体)
- [2. Repository](#2-repository)
- [3. 响应 DTO](#3-响应-dto)
- [4. Service](#4-service)
- [5. Controller](#5-controller)

## 完整业务案例

下面用“用户查询、创建、改名和删除”展示推荐分层。案例假设已引入 starter、mysql 和 repository；查询缓存模块仍是可选的。

### 1. 完整实体

```java
package com.example.app.user.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable(value = "sys_user", autoSchema = true)
@JoinIndex(fields = "username", unique = true)
public class UserEntity extends IdEntity<UserEntity> {
    @JoinField(name = "user_name", textLength = 64, description = "登录用户名")
    @JoinWhere
    private String username;

    @JoinField(description = "状态：1启用，0停用")
    @JoinWhere
    private Integer status;

    public UserEntity() { }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

Entity 保持非 final，提供无参构造和标准 getter/setter，以便编解码和代理。上例假设框架拥有该表全部列，因此明确设置 `autoSchema=true`；配套扫描配置如下：

这里的 `username` 并不能按驼峰规则转换成 `user_name`，所以示例中的 `@JoinField(name = "user_name")` 是必要的；如果 Java 属性本身命名为 `userName`，则应省略 `name`。

```yaml
yulinlin:
  repository:
    scan-packages:
      - "com.example.app.**.repository"
  mysql:
    schema-mode: CREATE
    schema-packages:
      - "com.example.app.**.domain"
```

如果生产表由 Flyway、Liquibase 或发布脚本管理，保留 `autoSchema=false` 默认值，不配置 `CREATE` 扫描，并在迁移脚本中创建同等的唯一索引。

### 2. Repository

```java
package com.example.app.user.repository;

import com.example.app.user.domain.UserEntity;
import com.yulinlin.data.core.anno.JoinCache;
import com.yulinlin.data.core.anno.JoinRepository;
import com.yulinlin.repository.dao.BaseRepository;

@JoinRepository
public interface UserRepository extends BaseRepository<UserEntity> {

    @JoinCache
    UserEntity findByUsernameEq(String username);
}
```

`BaseRepository` 已提供按 ID 查询、全量查询、新增、更新和删除。方法级 `@JoinCache` 让缓存意图可见；如果希望接口中全部查询默认缓存，也可标在接口上，但方法级更容易评审。不要在 insert/update/delete 方法上使用 `@JoinCache`。

### 3. 响应 DTO

```java
package com.example.app.user.api;

import com.example.app.user.domain.UserEntity;

public record UserResponse(String id, String username, Integer status) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getStatus());
    }
}
```

在 Service 内完成 Entity 到 DTO 的转换，可以避免 Controller 触发懒加载，也不会把事务绑定代理暴露给 JSON 序列化层。

### 4. Service

```java
package com.example.app.user.service;

import com.example.app.user.api.UserResponse;
import com.example.app.user.domain.UserEntity;
import com.example.app.user.repository.UserRepository;
import com.yulinlin.data.core.anno.JoinSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public UserResponse findByUsername(String username) {
        UserEntity user = repository.findByUsernameEq(requireText(username, "username"));
        if (user == null) throw new IllegalArgumentException("user not found");
        return UserResponse.from(user);
    }

    @Transactional
    public String create(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(requireText(username, "username"));
        user.setStatus(1);
        int affected = repository.insert(user);
        if (affected != 1) throw new IllegalStateException("user insert failed");
        return user.getId();
    }

    @Transactional
    @JoinSync
    public void rename(String id, String username) {
        UserEntity user = repository.findByIdEq(requireText(id, "id"));
        if (user == null) throw new IllegalArgumentException("user not found");
        user.setUsername(requireText(username, "username"));
        // 无需手动 update；提交前按主键生成部分更新。
    }

    @Transactional
    public void delete(String id) {
        int affected = repository.deleteByIdEq(requireText(id, "id"));
        if (affected != 1) throw new IllegalArgumentException("user not found");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
```

创建和删除是显式写入，只需事务。`rename` 依赖 setter 跟踪，因此同时使用 `@Transactional` 和 `@JoinSync`。查询、setter 与提交全部在同一个 Service 调用内完成。

### 5. Controller

```java
package com.example.app.user.api;

import com.example.app.user.service.UserService;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.starter.domain.IdPo;
import com.yulinlin.starter.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@ApiCrypto
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/by-username")
    public R<UserResponse> findByUsername(@Valid @RequestBody FindUserRequest request) {
        return R.ok(service.findByUsername(request.username()));
    }

    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody CreateUserRequest request) {
        return R.ok(service.create(request.username()), "创建成功");
    }

    @PostMapping("/rename")
    public R<Void> rename(@Valid @RequestBody RenameUserRequest request) {
        service.rename(request.id(), request.username());
        return R.ok();
    }

    @PostMapping("/delete")
    public R<Void> delete(@RequestBody IdPo request) {
        service.delete(request.getId());
        return R.ok();
    }

    public record FindUserRequest(@NotBlank String username) { }
    public record CreateUserRequest(@NotBlank @Size(max = 64) String username) { }
    public record RenameUserRequest(
            @NotBlank String id,
            @NotBlank @Size(max = 64) String username) { }
}
```

Controller 只处理 POST JSON 协议、校验和 DTO，不直接调用 Wrapper 或持有事务。查询和删除同样定义小型请求 DTO，不把参数拆到 URL。真实项目应将异常统一转换为约定的错误响应，不在每个 Controller 重复 try/catch。

---

下一步：返回[项目实践主题目录](README.md)按需选择相关专题。
