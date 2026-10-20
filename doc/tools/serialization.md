# 序列化、元组与扩展 SPI

[文档首页](../README.md) / [常用工具](README.md)

lang 序列化和框架扩展工具；缓存序列化见缓存专题。

## 本页导航

- [序列化、元组与框架扩展](#序列化元组与框架扩展)
- [Java 序列化](#java-序列化)
- [元组](#元组)
- [框架扩展 SPI](#框架扩展-spi)

## 序列化、元组与框架扩展

### Java 序列化

```java
byte[] bytes = SerializableUtil.serialize(value);
Object restored = SerializableUtil.unSerialize(bytes);
SerializableType copy = SerializableUtil.clone(value);
```

整个对象图必须实现 `Serializable`。Java 原生序列化通常比专用编码慢、体积大，且反序列化不可信字节存在安全风险；只处理本进程生成且可信的数据，不作为 HTTP、消息队列或跨语言协议。普通克隆使用 `ReflectionUtil.deepClone`。

### 元组

```java
MutablePair<String, Integer> pair = MutablePair.of("count", 3);
String left = pair.getLeft();

MutableTriple<String, Integer, Boolean> triple = MutableTriple.of("task", 1, true);
```

这些类是轻量位置容器，没有值语义的 equals/hashCode，也没有公开修改器。跨层 API、缓存 key 或领域模型优先定义 record/明确 DTO。

### 框架扩展 SPI

| 类型 | 用途 | 外部使用建议 |
| --- | --- | --- |
| BaseAnnotationBeanPostProcessor | 基于类/方法注解创建 Spring Advisor | 仅扩展框架 AOP；实现 getAnnotation 和 invoke |
| MethodHandlesUtil | privateLookupIn 和父接口 default/special 方法句柄 | 代理框架内部使用；受 JPMS opens 限制 |
| ProxyUtil | Spring CGLIB 子类代理 | JDK 25 优先接口/JDK Proxy；final 类和模块边界受限 |
| ReflectAsmUtil | 旧反射入口 | 已弃用，转用 ReflectionUtil；clear 是兼容空操作 |
| DateTimeJsonFormat、MoneyJsonFormat、CoinJsonFormat | Jackson serializer/deserializer 工厂 | 通常直接注册 YulinlinSimpModule |
| LongAdderJsonFormat、DoubleAdderJsonFormat | Adder JSON 数值格式 | 通常直接注册 YulinlinSimpModule |
| DateTimeStrJsonSerializer、EnumSerializer、EnumFormat | 可选展示/枚举格式 | 必须显式配置，不会由默认模块自动启用 |
| IEnum | label/value 枚举约定 | ORM EnumCoder 会优先持久化 value |
| CryptoDataResponse | 响应 data 可被安全模块替换的契约 | starter 的 R 已实现；普通项目按需实现 |

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
