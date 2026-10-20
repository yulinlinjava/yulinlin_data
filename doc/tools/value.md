# 日期、数字与字符串工具

[文档首页](../README.md) / [常用工具](README.md)

日期、金额、分页、字符串、随机数和 ID。

## 本页导航

- [日期、金额与分页](#日期金额与分页)
- [DateTime](#datetime)
- [Money、Coin 与 CodeObject](#moneycoin-与-codeobject)
- [Page](#page)
- [ListString](#liststring)
- [字符串、随机数与 ID](#字符串随机数与-id)
- [WeightedSelector](#weightedselector)

## 日期、金额与分页

### DateTime

`DateTime` 是基于 `LocalDateTime` 的不可变包装：`plus*`、`with*`、`beginOf*`、`endOf*` 都返回新对象，不修改原值。

```java
DateTime now = DateTime.now();
DateTime tomorrow = now.plusDays(1);
DateTime dayStart = now.beginOfDay();
DateTime dayEnd = now.endOfDay();

DateTime a = DateTime.parse("2026-10-10 12:30:00");
DateTime b = DateTime.parse("20261010123000");
DateTime c = DateTime.parse("2026/10/10", "yyyy/MM/dd");
long millis = a.getTime();
Date legacy = a.toDate();
```

默认解析支持 `yyyy-MM-dd HH:mm:ss`、`yyyyMMddHHmmss`、日期/月/年简写，以及 10 位秒时间戳和 13 位毫秒时间戳。时间戳和 `Date` 转换使用 JVM 默认时区；跨时区协议优先直接使用 `Instant`、`OffsetDateTime`。`isDate` 只是快速形态判断，不是严格日期校验。

### Money、Coin 与 CodeObject

```java
Money price = Money.of(12.34);
long stored = price.encode();       // 1234
Money decoded = new Money();
decoded.decode(stored);             // 12.34

Coin coin = Coin.of(8.5);
long coinStored = coin.encode();    // 85
```

`Money` 固定两位缩放，`Coin` 固定一位缩放；两者都继承 `Currency<Double>` 并实现 `CodeObject<Long>`。由于内部是 `Double`，不适合要求严格十进制精度、超大金额或复杂舍入的财务场景，这些场景使用 `BigDecimal`。

`CodeObject<T>` 是数据库值编解码约定：`encode` 输出持久化值，`decode` 把持久化值写回当前对象。

### Page

```java
Page<User> page = Page.of(users, total);
Page<UserDto> dtoPage = page.map(this::toDto);
Page<User> active = page.filter(User::isActive);
List<User> second = Page.page(users, 2, 20);
page.putValue("traceId", traceId);
```

`Page.page` 是从 1 开始的内存分页，不发 SQL，要求 `pageNumber >= 1`、`pageSize > 0`。`map` 遇到 null 会从返回 Page 的 total 中扣除；`filter` 保留原 total，适合“当前页后处理”，不表示重新统计数据库总数。`shuffle`、`random` 返回新 Page，不修改原 list；`randomOne` 不接受空列表。

### ListString

```java
ListString<Long> ids = new ListString<>();
ids.decode("10,20,30", Long.class);
String value = ids.encode();

ListString<String> tags = ListString.ofString("java,jdk25");
```

默认分隔符是进程级静态字段 `ListString.splitStr`。支持 String、Integer、Long、Double、Byte、Float；内容本身包含分隔符时不能无损往返，也不处理转义。业务优先保持默认逗号，避免运行期间修改全局分隔符。

## 字符串、随机数与 ID

```java
String field = StringUtil.columnToJava("user_name"); // userName
String column = StringUtil.javaToColumn("userName"); // user_name
String text = StringUtil.removeLine(" a\n b\t");     // ab
String rendered = StringUtil.renderString("hi ${name}", Map.of("name", "Lin"));

int value = RandomUtil.randomInt(10); // [0, 10)
char tokenChar = RandomUtil.randomChar();

Snowflake snowflake = new Snowflake(1, 2);
long id = snowflake.nextId();
```

- `StringUtil.isNull` 只判断 null 或空串，不等价于 `isBlank`；`removeLine` 会移除 ASCII 空白。
- `renderString` 是简单 `${key}` 替换，不是表达式引擎；值不能为 null。
- `RandomUtil` 基于 `ThreadLocalRandom`，适合抽样和非安全随机，不能生成密码、token、IV 或密钥。
- `Snowflake` 的 workerId 和 dataCenterId 均为 0～31；同一时间范围内的不同进程必须使用不同组合。`nextId` 是同步线程安全的，时钟大幅回拨会拒绝生成。

### WeightedSelector

```java
WeightedSelector<String> selector = new WeightedSelector<>();
selector.add("A", 1);
selector.add("B", 3);
String selected = selector.select();
```

权重小于等于 0 会忽略；没有正权重项时 `select` 抛异常。添加操作同步并发布到并发有序表，热点选择路径不使用共享 `Random`，支持并发读取；`list` 返回不可修改视图。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
