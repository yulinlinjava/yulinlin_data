# 全文检索与高亮

[文档首页](../README.md) / [ORM 开发](README.md)

统一 match/highlight API、中文分词和数据库差异。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [全文匹配与高亮](#全文匹配与高亮)

## 全文匹配与高亮

PostgreSQL 中文全文检索先在完整表实体上标记需要建立索引的文本字段：

```java
@JoinTable(value = "video", autoSchema = true)
public class Video {
    @JoinMeta(primaryKey = true)
    private String id;

    @JoinField(name = "title", fullText = true, textLength = 200)
    private String title;

    @JoinField(name = "content", fullText = true, textType = TextTypeEnum.text)
    private String content;

    // getter/setter
}
```

启动扫描在 PostgreSQL 上为 title、content 分别生成可单字段命中的 GIN 表达式索引。查询时 `match` 指定检索字段，`highlight` 把高亮后的摘要直接写回同名结果字段，不需要额外的 titleHighlight DTO 属性：

```java
List<Video> videos = ModelSelectWrapper.newInstance("postgresql", Video.class)
        .match(Video::getTitle, "Java 性能")
        .highlight(Video::getTitle)
        .orderByDesc("id")
        .selectList();
```

也可以同时检索多个字段：

```java
List<Video> videos = ModelSelectWrapper.newInstance("postgresql", Video.class)
        .or(or -> or.match(Video::getTitle, keyword)
                    .match(Video::getContent, keyword))
        .fieldMeta(fields -> fields
                .field("title")
                .field("content")
                .highlight("title")
                .highlight("content"))
        .selectList();
```

只有同一查询中实际出现原生 `match(field, ...)` 的字段才调用 `ts_headline`。单独写 `highlight()`、字段未声明 `fullText=true`、原始 SQL查询，都会返回普通原字段。计数只解析 WHERE，不生成高亮表达式。公共高亮标记与片段设置在 `yulinlin.postgresql.highlight`，PostgreSQL 专属摘要长度在 `yulinlin.postgresql.full-text`；pg_jieba 的安装和完整 YAML 见[PostgreSQL 中文全文检索](../datasources/postgresql.md#postgresql-中文全文检索)。

MySQL、SQLite、H2 当前把 `match()` 解释为 `%关键词%` LIKE，并忽略 `highlight()`；这只是兼容回退，大表检索应改用 PostgreSQL 原生全文能力或 Elasticsearch。Elasticsearch 的 `match()` 使用原生 MatchQuery；MongoDB 保持现有正则兼容语义。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
