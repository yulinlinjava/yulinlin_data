# ORM 常见边界与排障

[文档首页](../README.md) / [监控与排障](README.md)

写入、查询、方言和模型使用的常见误区。

## 本页导航

- [使用边界与排障](#使用边界与排障)

## 使用边界与排障

- 更新通常跳过 null；普通 copyProperties 或自动更新代理的 null 跳过不等于数据库清列。
- insertBefore/updateBefore 可能填充字段，表结构必须匹配实体实际继承字段。
- 查询缓存保存查询结果；标准写操作默认在事务提交成功后清理相关表命名空间，`clearCache(false)` 会关闭单次清理，TTL 是最终兜底。要求事务内读己之写或绝对强一致的查询使用 `CacheMode.NONE`。
- selectOne 返回 null 时先处理“未找到”，不将它解释成解析错误或唯一性保证。
- 日期范围为空先核对列类型、格式、时区与条件，不默认归咎于数据库驱动。
- 表达式、JOIN、JSON 路径、行锁按目标库核对；没有跨库 SQL 自动翻译器。
- NoSuchMethodError 先检查编译与运行时 JAR 是否一致；源码修复不等于已经替换部署产物。

关联增强见 [关联与事务](../relations/README.md)，工具与复制语义见 [常用工具](../tools/README.md)，源码扩展及验收记录见 [内部维护](../internal/README.md)。

---

下一步：返回[监控与排障主题目录](README.md)按需选择相关专题。
