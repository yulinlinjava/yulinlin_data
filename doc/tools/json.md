# JSON 转换

[文档首页](../README.md) / [常用工具](README.md)

常用 JSON 转换方式与泛型类型。

## 本页导航

- [JSON 转换](#json-转换-1)

## JSON 转换

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.lang.json.JsonUtil;
import demo.tools.ToolUser;
import java.util.List;

String json = JsonUtil.toJson(new ToolUser());
ToolUser user = JsonUtil.parseJson(json, ToolUser.class);
List<ToolUser> users = JsonUtil.parseJson("[]", new TypeReference<List<ToolUser>>() {});
```

JsonUtil.to(source, Target.class) 经 JSON 做类型转换，受 Jackson 注解/配置影响，不是对象图克隆，不保持任意循环与对象身份。

`JsonUtil` 独立模式有自有 mapper；starter 会用 Spring `ObjectMapper` 替换它。`setMapper` 是进程级全局设置，应在启动阶段完成，不能在并发请求中来回切换。`HttpUtil` 静态客户端的 mapper 是另一套，不自动与 `JsonUtil` 同步。

`YulinlinSimpModule` 自动支持以下值：

| Java 类型 | JSON 形式 |
| --- | --- |
| DateTime | `yyyy-MM-dd HH:mm:ss` 字符串 |
| Money、Coin | JSON 数字 |
| LongAdder、DoubleAdder | 当前累计值 |

手动创建 Jackson mapper 时这样注册：

```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new YulinlinSimpModule());
JsonUtil.setMapper(mapper);
```

`DateTimeStrJsonSerializer` 输出“刚刚、N 分钟前”等相对文本；`EnumSerializer` 输出实现 `IEnum` 的 label。它们没有加入 `YulinlinSimpModule`，必须通过 Jackson 注解或模块显式注册。`EnumFormat` 也是扩展构件，不会自动接管全部枚举。

`JsonUtil.clone` 是保留的 JSON 往返接口，只适合 Jackson 可序列化模型；异构集合、循环引用和对象身份不可靠。通用深克隆优先使用 `ReflectionUtil.deepClone`。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
