# Elasticsearch 接入

[文档首页](../README.md) / [数据源](README.md)

索引与 Mapping 初始化、配置和能力边界。

## 本页导航

- [Elasticsearch 结构初始化](#elasticsearch-结构初始化)
- [普通查询计数与分页](#普通查询计数与分页)
- [查询能力边界](#查询能力边界)

## Elasticsearch 结构初始化

Elasticsearch 与 JDBC 模块复用 core 的实体扫描和 Schema 配置。默认 `schema-mode=NONE`，不会访问或修改索引；启用时配置：

```yaml
yulinlin:
  elasticsearch:
    url: http://localhost:9200
    log: true
    map-underscore-to-camel-case: true
    schema-mode: CREATE
    schema-packages:
      - com.example.search.entity
    highlight:
      start-tag: "<mark>"
      end-tag: "</mark>"
      max-fragments: 2
      fragment-delimiter: "..."
```

模块使用 Elasticsearch Java Client `9.5.4` 和其默认 `Rest5Client`。`CREATE` 会创建缺失索引和基础 Mapping，并给已有索引补充缺失字段；`VALIDATE` 只检查索引、字段和字段类型，不修改结构。普通字符串映射为 `keyword`，`@JoinField(fullText = true)` 或 `textType = text` 映射为 `text`；复杂 JSON 对象保留 Elasticsearch 动态映射。已存在字段类型不一致时不会强制修改，而是要求手工迁移索引。查询调用 `.highlight(field)` 后使用公共标签与片段数，并按 `fragment-delimiter` 合并返回的多个片段到原字段。

## 普通查询计数与分页

普通 `SelectWrapper`、`ModelSelectWrapper.count()` 和 `QueryRequest.count()` 已接通 ES Count API：`CountWrapper` 解析为独立的 `CountRequest`，会话调用原生 `/{index}/_count`，再通过公共 `total` 结果协议返回。不会读取一页 hits 后取数量，也不会使用搜索返回的估算总数。Count API 的文档语义见 [Elasticsearch 官方说明](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-count)。

计数复用索引、WHERE 条件、字段映射与显式 ES 字段元数据；忽略页码、每页条数、排序、投影、高亮和行锁标记，不修改原 Wrapper。没有 WHERE 时统计指定索引中的全部文档；必须明确指定索引，不允许缺失或空索引意外变成全库统计。显式索引通配符仍可使用。

在已配置会话和准备好 `people` 索引的项目中：

```java
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import java.util.Map;

var select = new SelectWrapper<>().table("people").page(2, 20);
select.fields().field("status");
select.where().eq("status", "active");
var request = QueryRequest.newInstance(Map.class, select);
request.setSession("elasticsearch");

int total = request.count();       // 所有符合条件的文档数量，不是第二页的条数
var page = request.selectPage();   // 内部自行调用 count，再按原查询执行 search
```

通常只需调用 `count()` 或 `selectPage()` 中的一个；示例连续调用是展示两种入口。未命中缓存时，`selectPage()` 的计数与列表是两次独立请求，不保证并发写入下属于同一快照。计数仍参与已有的查询缓存、执行监听与错误处理；相同条件不同分页共享逻辑计数缓存，已有缓存一致性规则不变。

统一接口仍返回 `Integer`，有效范围是 `0` 至 `2147483647`；超过范围明确抛错，不截断为负数。ES 请求异常原样传播，失败分片或无效负数结果会报错，不把错误当成 0 或缓存不完整总数。框架不会设置提前终止计数的 `terminate_after`。

**分组计数不在本次支持范围。** `GroupWrapper` 的 count（包括依赖该计数的分组分页入口）明确抛出不支持异常：文档总数、当前返回的桶数都不能代替完整分组数，尤其不能忽略 HAVING。原有分组列表查询、普通 select、投影、排序和高亮实现不变。复杂分组总数请使用原生聚合并由业务明确精度和分页语义。

[ES 计数回归测试](../../elasticsearch/src/test/java/com/yulinlin/elasticsearch/EsCountTest.java)使用真实 Java Client 和内存模拟传输层，覆盖请求解析、公开 Session count/page、计数边界、分片失败、异常传播、缓存、监听和原查询保持；不连接外部 ES，不能替代真实集群与权限配置的集成验证。

## 查询能力边界

框架保留普通字段条件与 `and/or/not` 分组，不提供统一或 ES 专属的 nested 查询入口。`NestedNode`、对应 `NestedParse` 和注册已删除，无兼容开关；使用过这些类的项目需要移除调用并重新编译，各模块使用同一版本。

点路径字段仍作为普通字段解析，例如 `.eq("profile.name", "Alice")` 生成普通 term 条件，不会自动包装为 nested 查询，也不能作为旧 `NestedNode` 的等价替代。条件分组只表示布尔组合，不表示数组元素的关联匹配。

确需 nested 查询时，业务直接使用 Elasticsearch 原生客户端并自行维护相应 Mapping 和查询语义；自动配置提供的 `ElasticsearchClient` 仍保留。这类调用直接访问 ES，不经过 ORM 的路由、结果编解码或查询缓存，不能假定享有 Wrapper 的执行行为。本次删除不修改已有索引、Mapping 或文档数据。

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
