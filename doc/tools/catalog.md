# lang 定位与类型目录

[文档首页](../README.md) / [常用工具](README.md)

先查工具所在模块和公开类型，再阅读对应专题。

## 本页导航

- [lang 模块定位](#lang-模块定位)
- [lang 公开类型目录](#lang-公开类型目录)
- [常见问题](#常见问题)

## lang 模块定位

只使用基础工具时可以直接引入：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>lang</artifactId>
    <version>3.0</version>
</dependency>
```

`lang` 不是零依赖 JAR：JSON API 使用 Jackson，注解、泛型扫描与 AOP 扩展使用 Spring/AspectJ。若项目已经引入完整框架，无需重复声明。

本页把 API 分为三类：

- 业务工具：`JsonUtil`、`ReflectionUtil`、`DateTime`、`Page`、`StringUtil` 等，可直接调用。
- 轻量基础设施：`ExpiryMap`、`SmartCache`、`ResourcePool`、事件和线程工具，调用方需要管理生命周期与并发边界。
- 框架扩展 SPI：AOP、JSON 格式器、代理和安全响应接口，只有扩展框架时才实现或直接调用。

## lang 公开类型目录

下面按包列出当前全部公开类型，便于外部 AI 判断准确入口。未列为首选的兼容类型也没有删除。

| 包 | 公开类型 | 定位 |
| --- | --- | --- |
| `lang.aop` | BaseAnnotationBeanPostProcessor、AnnotationAdvisor | Spring AOP 扩展 SPI |
| `lang.cache` | ExpiryMap、ResourcePool | JVM 轻量过期缓存、资源池 |
| `lang.enums` | IEnum | 枚举 label/value 契约 |
| `lang.event` | AbstractEventPublishManager、EventPublishManager、IEvent、IEventHandler | JVM 内同步/异步事件 |
| `lang.json` | JsonUtil、YulinlinSimpModule、DateTimeJsonFormat、DateTimeStrJsonSerializer、MoneyJsonFormat、CoinJsonFormat、LongAdderJsonFormat、DoubleAdderJsonFormat、EnumFormat、EnumSerializer | Jackson 转换与扩展 |
| `lang.lambda` | LambdaPropertyFunction、LambdaUtils | 可序列化属性方法引用解析 |
| `lang.lazy` | SmartCache | 首次同步、过期异步刷新的单值缓存 |
| `lang.reflection` | ReflectionUtil、GenericUtil、AnnotationUtil、SerializableUtil、MethodHandlesUtil、ProxyUtil、ReflectAsmUtil | 反射、泛型、注解、序列化和代理 |
| `lang.security` | CryptoDataResponse | security 模块响应加密 SPI |
| `lang.tuple` | Triple、MutablePair、MutableTriple | 轻量位置容器 |
| `lang.util` | DateTime、Page、StringUtil、RandomUtil、Snowflake、WeightedSelector、SegmentLock、ThreadUtil、ListString、CodeObject、Currency、Money、Coin、ClassUtil、ScanUtils、BaseGeneric、GenericClass、GenericField、GenericMethod | 通用值、并发、扫描和兼容泛型工具 |

`DeepCopies`、`ValueCopies`、`HandleAccess` 是包内实现，不是外部 API。

## 常见问题

| 问题 | 优先检查 |
| --- | --- |
| ReflectionUtil.property 的 NoSuchMethodError | 编译与运行时 lang/core 是否同一产物 |
| DTO 复制字段缺失 | 名称、可写属性、null 跳过及嵌套类型是否相容 |
| 克隆列表后共享引用不同 | 整体克隆还是每元素独立调用 |
| SmartCache 过期仍拿到旧值 | 这是 stale-while-revalidate；首次加载才阻塞 |
| ExpiryMap 实例不再使用 | 调用 shutdown 取消该实例清理任务 |
| ResourcePool 后续 acquire 卡住 | 检查每次借出是否恰好 release 一次 |
| Lambda 解析失败 | 参数是否为 LambdaPropertyFunction/其他可序列化 Lambda |
| DateTime 时间戳偏移 | JVM 默认时区是否符合业务协议 |
| Java 反序列化是否能处理请求数据 | 不要反序列化不可信输入 |
| HTTP 超时配置没生效 | Spring 客户端、静态客户端、手动构造和单次覆盖分别核对 |
| 下载成功但 body 为空 | downloadTo 内容在文件中 |
| 404 检查返回 false | 不代表存在，需区分网络/状态异常 |

本文按当前源码和 JDK 25 行为核对；HTTP 地址、文件路径及业务类型是示例，需要替换。工具类是进程内能力，不自动提供分布式一致性、持久化、重试或安全随机。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
