# SQL JOIN

[文档首页](../README.md) / [ORM 开发](README.md)

SQL 联表查询；与应用侧 JoinQuery 关联加载区分。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [SQL JOIN](#sql-join-1)

## SQL JOIN

SQL JOIN 与[应用侧 JoinQuery](../relations/query.md) 不是同一功能。以下完整投影类假设 sys_user、sys_dept 表存在，列名与示例一致；使用 Lombok 生成访问方法：

```java
package demo.dto;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;

@Data
@JoinTable(left = "sys_user a", right = "sys_dept b", on = "a.sys_dept_id = b.id")
public class UserDepartmentView {
    @JoinField(name = "a.id")
    private String id;
    @JoinField(name = "a.username")
    private String username;
    @JoinField(name = "b.dept_name")
    private String departmentName;
}
```

方法体查询：`ModelSelectWrapper.newInstance("mysql", UserDepartmentView.class).eq("id", id).selectList()`。更多表可用 JoinTableList。表表达式、别名和 ON 是可信代码，不能直接拼接 HTTP 输入；不能复用旧文档的 JoinPrimary、lambda().eq 或 getSql 调用来猜测当前 API。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
