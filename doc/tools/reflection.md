# 反射复制与深克隆

[文档首页](../README.md) / [常用工具](README.md)

属性复制、嵌套对象和深度克隆的行为边界。

## 本页导航

- [反射复制与深克隆](#反射复制与深克隆-1)

## 反射复制与深克隆

以下完整普通 Bean 不依赖 ORM；作为本节和 JSON 示例模型：

```java
package demo.tools;

import java.util.ArrayList;
import java.util.List;

public class ToolUser {
    private String name;
    private List<String> tags = new ArrayList<>();
    public ToolUser() { }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
```

业务方法体片段：

```java
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import demo.tools.ToolUser;

ToolUser user = ReflectionUtil.newInstance(ToolUser.class);
ReflectionUtil.invokeSetter(user, "name", "alice");
Object name = ReflectionUtil.invokeGetter(user, "name");

ReflectionUtil.PropertyAccess access = ReflectionUtil.property(ToolUser.class, "name");
access.set(user, "bob");
Object value = access.get(user);

ToolUser deep = ReflectionUtil.deepClone(user);
ToolUser target = new ToolUser();
ReflectionUtil.copyProperties(user, target);
```

| API | 语义 |
| --- | --- |
| newInstance(Class) | 可访问无参构造 |
| invokeGetter/invokeSetter | 名称和嵌套路径；不推定中间对象自动创建 |
| invokeMethod(bean, name, args...) | 根据名称和参数找方法；歧义报错，变长参数传声明数组 |
| property(Class, name) | 单一属性访问器，可缓存；不是嵌套路径解析器 |
| clone(value) | 浅复制 |
| deepClone(value) / clone(value, true) | 对象图深复制，null 返回 null |
| copyProperties(source, target) | 同名属性深复制到已有目标；跳过缺少属性及源 null |

深克隆覆盖支持的 Bean、集合、Map 键值、数组和可变值，保留对象图的循环与共享引用。每次调用使用独立身份跟踪上下文，不把 DeepCopies 改成跨请求共享单例。

整体 deepClone(list) 保留跨元素共享关系；逐条 deepClone 不保留跨调用共享关系。这两种方式不能仅凭速度互换。

使用限制：

- Bean 需要无参构造和可写属性；record、不可写 final 字段、线程/流/连接不作为通用复制目标。
- 不自动把嵌套实体转换为另一种 DTO，也不自动转换集合泛型。
- 不可变值可以复用；包装集合可能变成普通可变集合，不保证保留不可修改/同步包装语义。
- copyProperties 失败可能已部分修改目标，没有回滚；null 跳过不等于 SQL NULL 更新。
- 私有成员访问仍受 Java 模块 opens 限制，不绕过任意访问控制。
- 当前基于 MethodHandle，ReflectAsmUtil 是弃用兼容入口；Kryo 不是 lang 的克隆后端。
- 性能实测必须看相同模型、粒度和环境；历史 JMH 数据在 [内部维护](../operations/clone-benchmark.md#历史克隆基准)，不作当前通用排名。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
