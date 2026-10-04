# 反射与深复制

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`lang/src/main/java/com/yulinlin/data/lang/reflection/`。若与实际安装版本冲突，以该版本源码为准。

下例的 `demo.domain.DemoUser` 定义在 `10-orm.md`；只使用反射工具时可换成自己的有无参构造器、可写属性的普通 Bean，不需要启动 ORM。

```java
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import demo.domain.DemoUser;

DemoUser user = ReflectionUtil.newInstance(DemoUser.class);
ReflectionUtil.invokeSetter(user, "username", "alice");
Object name = ReflectionUtil.invokeGetter(user, "username");

ReflectionUtil.PropertyAccess access = ReflectionUtil.property(DemoUser.class, "username");
access.set(user, "bob");
Object value = access.get(user);

DemoUser clone = ReflectionUtil.deepClone(user);
DemoUser target = new DemoUser();
ReflectionUtil.copyProperties(user, target);
```

| API | 当前语义 |
| --- | --- |
| `newInstance(Class<E>)` | 需要可访问的无参构造器 |
| `invokeGetter` / `invokeSetter` | 按名字访问，支持嵌套路径；中间对象不要假设会自动创建 |
| `invokeMethod(obj, name, args...)` | 按方法名和参数解析；歧义报错，变长参数需传声明的数组 |
| `property(Class, String)` | 单一属性访问器，可缓存复用，不是嵌套路径解析器 |
| `clone(value)` | 浅复制，不能用于保证嵌套对象独立 |
| `deepClone(value)` / `clone(value, true)` | 对象图深复制；null 输入返回 null |
| `copyProperties(source, target)` | 同名属性深复制到已有目标；跳过源缺少的属性及源 null 值 |

深复制包括 Bean、集合元素、Map 键值、数组及支持的可变值类型；支持范围内保留循环与共享引用。每次 deepClone 都有独立身份跟踪上下文，这是防止不同操作污染，不应直接改成共享单例。

重要边界：

- Bean 需要无参构造器及可写属性；record、不可写 final 字段、线程/流/连接等资源对象不作为通用克隆目标。
- 不自动把源嵌套实体转为不同类型 DTO，也不自动转换集合泛型。
- 不可变值可复用；集合包装器可能变为标准可变集合，不保证保留不可修改或同步包装语义。有序集合复用比较器。
- copyProperties 失败时目标可能已部分修改，不提供回滚。源 null 跳过语义也不等于数据库“设为 NULL”。
- 私有成员访问仍受 Java 模块 opens 限制；不是能绕过任意访问控制。
- 当前基于 MethodHandle；ReflectAsmUtil 是弃用兼容入口，新代码不要使用。Kryo 仅出现在 admin 性能对比中，不是 lang 的深克隆后端。
- 整体 deepClone(list) 保留跨元素共享引用；逐条克隆不能保留跨调用共享关系。不要仅为速度交换这两种语义。
