# JSON 与其他工具

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`lang/src/main/java/com/yulinlin/data/lang/json/JsonUtil.java`；`lang/.../util/`；`common/.../util/`。若与实际安装版本冲突，以该版本源码为准。

下例的 `demo.domain.DemoUser` 定义在 `10-orm.md`，也可换成自己的普通 Bean。

### JSON

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.lang.json.JsonUtil;
import demo.domain.DemoUser;
import java.util.List;

String json = JsonUtil.toJson(new DemoUser());
DemoUser user = JsonUtil.parseJson(json, DemoUser.class);
List<DemoUser> users = JsonUtil.parseJson("[]", new TypeReference<List<DemoUser>>() {});
```

`JsonUtil.to(source, Target.class)` 经 JSON 转换，受 Jackson 注解/配置影响，不是对象图克隆。不要依赖它保留循环引用与对象身份。JsonUtil 默认有自有 mapper；starter 会将它设置为 Spring ObjectMapper，不能把默认独立模式配置当作所有环境的配置。HttpUtil 静态默认 mapper 又是独立配置，不会自动和 JsonUtil 同步。

### 常用入口和注意点

| 类 | 使用示例 / 签名 | 边界 |
| --- | --- | --- |
| `StringUtil` | `isNull(text)`、`isNotNull(text)` | 只判断 null/空串，不等于 isBlank；纯空格不是空串 |
| `StringUtil` | `javaToColumn(name)`、`columnToJava(name)` | 字符串转换工具，不代表所有 ORM 路径自动采用此规则 |
| `DateTime` | `DateTime.now()`、`DateTime.parse(text, "yyyy-MM-dd HH:mm:ss")` | 框架自有日期类型；不要当作不可变 java.time 类型 |
| `Page` | `Page.of(list)`、`Page.page(list, 1, 20)` | 后者是内存分页，不执行 SQL；调用前校验正数页码/页大小 |
| `SnowflakeUtil` | `nextIdStr()`、`nextId()` | 默认节点配置含随机因素，多实例唯一性需显式规划，不能承诺随机节点绝不冲突 |
| `TreeUtil` | `buildTree(List<E>)`，E 实现 `com.yulinlin.common.domain.ITreeNode` | 原地给父节点追加子节点；先清理旧 children，校验重复 id/环，缺失父节点的节点会成为根 |
| `R` | `R.newInstance(data)` | 统一响应包装不是 HTTP 状态设置器；不要猜测存在 `R.ok()` / `R.success()` |

缓存、线程池、锁、金额、事件、服务基类等工具还存在于源码中，但本文未逐个验证其业务契约。需要时先读对应类，不仅凭类名生成调用，更不要把金额精度、分布式锁或线程安全能力自行补全。
