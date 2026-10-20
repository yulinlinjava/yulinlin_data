# 实体映射与最小模型

[文档首页](../README.md) / [ORM 开发](README.md)

定义 DemoUser，后续 CRUD 示例沿用这个模型。

## 本页导航

- [最小实体和配套表](#最小实体和配套表)

## 最小实体和配套表

以下完整类放在 `demo/domain/DemoUser.java`，提供无参构造和 getter/setter。IdEntity 提供 String 主键及插入前的应用侧 ID 生成。

```java
package demo.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable(value = "ai_demo_user", autoSchema = true)
public class DemoUser extends IdEntity<DemoUser> {
    @JoinField(name = "user_name")
    @JoinWhere
    private String username;

    @JoinField
    @JoinWhere
    private Integer status;

    public DemoUser() { }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

MySQL/PostgreSQL/SQLite/H2 若已按[Schema 专题](../datasources/schema.md)配置启动扫描，DemoUser 由应用启动阶段创建/校验；否则必须提前准备。下列 DDL 只在自己的示例数据库执行，不把它当作生产迁移脚本：

```sql
CREATE TABLE ai_demo_user (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    user_name VARCHAR(100),
    status INT
);
```

SuperEntity 在 IdEntity 上增加 crtTime、uptTime 和填充逻辑，使用它时表必须有对应列。非持久化属性显式用 `@JoinField(exist = false)`，不能依赖“没有注解就一定忽略”。

| 注解 | 用途 |
| --- | --- |
| JoinTable | 表或 SQL JOIN 映射；autoSchema 默认 false，只有完整结构实体明确设为 true 才参与启动 Schema |
| JoinField(name = "...") | Java 属性与列名映射 |
| JoinField(textLength = 120) | 自动 Schema 的 VARCHAR 字符上限；字符串主键默认 32，显式长度按注解，仍受数据库限制 |
| JoinField(textType = TextTypeEnum.text) | 自动 Schema 使用大文本；不能作为主键或框架声明索引 |
| JoinField(description = "...") | 列用途说明；MySQL/PostgreSQL/H2 新表或新列写入注释，SQLite 不落库 |
| JoinField(fullText = true) | PostgreSQL 为该文本列维护 pg_jieba GIN 全文索引；其他数据库忽略 |
| JoinField(exist = false) | 排除非数据库列 |
| JoinField(update = false) | 排除更新字段 |
| JoinMeta(primaryKey = true) | 主键元信息；不是 JoinPrimary |
| JoinIndex(fields = {...}) | MySQL/PostgreSQL/H2/SQLite 启动维护普通或唯一联合索引；字段写 Java 属性名 |
| JoinWhere | 对象属性有值时参与条件 |
| JoinField(version = true) | 版本字段；具体支持路径按代理与实际更新实现核对 |

自定义主键建议同时声明 JoinField、JoinMeta、JoinWhere。重命名主键列时要检查删除、部分更新等实际映射，不仅查看 SELECT SQL。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
