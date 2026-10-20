# Lambda、泛型与包扫描

[文档首页](../README.md) / [常用工具](README.md)

方法引用、泛型解析、注解和包扫描。

## 本页导航

- [Lambda、泛型与包扫描](#lambda泛型与包扫描-1)
- [LambdaUtils](#lambdautils)
- [GenericUtil](#genericutil)
- [AnnotationUtil 与 ScanUtils](#annotationutil-与-scanutils)

## Lambda、泛型与包扫描

### LambdaUtils

```java
LambdaPropertyFunction<User> property = User::getUsername;
String method = LambdaUtils.lambdaMethodName(property);          // getUsername
Field field = LambdaUtils.lambdaMethodNameToField(property);     // username
Class<User> owner = LambdaUtils.lambdaMethodNameToClassName(property);
```

参数必须是可序列化 Lambda，通常使用 `LambdaPropertyFunction<T>`。不要传普通 `Function`、匿名内部类或任意对象。实现只缓存 lambda class 的 `writeReplace` 访问器，不缓存捕获参数，因此不会把首个捕获对象长期保留。

### GenericUtil

```java
Class<?> entityType = GenericUtil.forClass(UserRepositoryImpl.class, BaseRepository.class, 0);
Class<?> elementType = GenericUtil.forField(User.class.getDeclaredField("roles"), 0);
Class<?> returnType = GenericUtil.forMethodReturnType(method, 0);
Class<?> parameterType = GenericUtil.forMethodParameter(method, 0, 0);
```

解析不到具体类型时返回 null。`GenericUtil` 只在首次调用时进入 Spring `ResolvableType`，之后按源/声明类使用 `ClassValue` 缓存类、字段、方法参数和返回值的最终类型；解析失败的 null 也会缓存。类卸载时其缓存可一起回收，测试或动态类替换可调用 `clearCache(Class<?>)` 或 `clearCache()`。

`BaseGeneric`、`GenericClass`、`GenericField`、`GenericMethod` 是旧的链式泛型模型，仍为兼容保留；新外部代码优先用 `GenericUtil`。`GenericClass.newInstance` 同样使用 `ClassValue` 缓存，不阻止类加载器卸载。

### AnnotationUtil 与 ScanUtils

```java
JoinTable table = AnnotationUtil.findAnnotation(User.class, JoinTable.class);
Marker marker = AnnotationUtil.findAnnotation(method, Marker.class, 0); // 第 0 个参数上的注解

Set<Class<?>> services = ScanUtils.scanSubclass(
        "com.example.**.service", BaseService.class, true);
```

`AnnotationUtil` 使用 Spring 合成注解规则；AspectJ 重载用于切面内部。`ScanUtils.scanClass(path, false)` 返回具体类，传 true 返回接口或抽象类；扫描会读取 class 元数据并加载匹配 Class，但不会初始化静态代码。通配路径依赖 Spring resource pattern，库/应用边界上优先传明确根包，避免扫描整个 classpath。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
