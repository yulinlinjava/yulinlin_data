# Elasticsearch 接入

[文档首页](../README.md) / [数据源](README.md)

索引与 Mapping 初始化、配置和能力边界。

## 本页导航

- [Elasticsearch 结构初始化](#elasticsearch-结构初始化)

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

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
