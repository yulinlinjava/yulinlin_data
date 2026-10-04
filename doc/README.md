# 文档入口：按任务读取

这是当前使用文档的唯一目录。不要将 doc 下所有文件一次性提供给 AI：旧示例、报告与当前 API 的用途不同。

## 给 AI 的两种方式

- **AI 能读取仓库：** 从根目录 `llms.txt` 进入，先读 `topics/00-context.md`，再按下表选择专题。
- **只能上传一个附件：** 提供 [AI接入指南.md](AI接入指南.md)。它由全部当前专题自动合并，包含一致的代码示例与边界，不包含存档和历史跑分。

## 当前专题与最小上下文

| 任务 | 需要读取 | 内容 |
| --- | --- | --- |
| 所有任务 | [00-context](topics/00-context.md) | 版本、准确包名、约定、模块范围 |
| ORM CRUD | [10-orm](topics/10-orm.md) + [20-transactions](topics/20-transactions.md) | 依赖、配置、实体、配套表、完整 Service、Spring 事务 |
| Spring 事务排查 | [20-transactions](topics/20-transactions.md) | 支持路径与多路由/异步边界 |
| HTTP 请求/文件 | [30-http](topics/30-http.md) | JSON、表单、上传下载、超时、404、异常 |
| 反射/复制 | [40-reflection](topics/40-reflection.md) | API、null、引用关系、DTO 与克隆区别 |
| JSON/常用工具 | [50-utilities](topics/50-utilities.md) | JSON、字符串、日期、树、ID、响应包装 |
| 报错/生成前检查 | [90-troubleshooting](topics/90-troubleshooting.md) | 排障表、交付检查、外部 AI 提示模板 |

40/50 中的 DemoUser 是 10 中的示例模型；仅用工具类可以替换为自己的普通 Bean，不要求启动 ORM。

## 文档状态与可信度

| 分类 | 位置 | 使用规则 |
| --- | --- | --- |
| 当前契约与示例 | `topics/` | 维护源；标记源码基线与未验证范围，优先读取 |
| 单文件 AI 导出 | `AI接入指南.md` | 自动生成，不单独编辑，不与专题重复投喂 |
| 历史案例 | [archive](archive/README.md) | 保留原文；仅查背景，不直接据此生成代码 |
| 性能实测 | [2026-10-01 克隆报告](深度克隆性能对比报告.md) | 仅代表当时机器、代码和数据，不是最新通用排名 |
| 性能运行说明 | [JMH 指南](深度克隆JMH基准.md) | 按需读取；当前未重新运行 |
| SQL 样例 | `admin.sql`、`code.sql`、`quartz.sql` | 历史演示数据，不是当前迁移方案，不自动执行 |

专题的主体契约延续 2026-10-01 源码核对；2026-10-04 重组时复查了 POM、HTTP 超时工厂及 Spring 事务切面。没有声称所有示例重新编译或集成测试通过。若安装包与源码不同，先核对依赖再生成代码。

## 维护规则

1. 每个主题只维护 `topics/` 中的一份正文；已移除重复的旧指南和旧路径跳转页，统一从本目录进入。
2. 修改 API 文档时同时核对源码签名、异常、null、线程/引用语义，并更新核对说明。
3. 区分完整类、方法体片段、占位值和前置条件，不声称未运行的示例已验证。
4. 生成并检查单文件导出：

```powershell
./doc/build-ai-docs.ps1
./doc/build-ai-docs.ps1 -Check
```

脚本使用 PowerShell 7，只处理文档；检查生成文件同步、当前文档相对链接和代码围栏，不联网、不编译项目。它不验证 Java API 的语义或编译正确性。

新增专题时更新脚本清单、此目录及根 `llms.txt`。高级 JOIN/级联等主题目前保留为历史资料，未验证之前不要提升为“当前推荐”。
