# 项目实践

[返回文档首页](../README.md)

复用 common 模型，按推荐分层开发业务和 RBAC。

## 按需阅读

| 小专题 | 解决什么问题 |
| --- | --- |
| [项目开发规范](conventions.md) | 分层、事务、缓存、日志、批量和上线前检查。 |
| [公共实体与页面参数](models.md) | PagePo、JoinWhere、R、IdEntity、SuperEntity 和 TreeEntity。 |
| [通用 Service 与树服务](service.md) | 复用 ISuperService/ITreeService，区分 Repository 职责。 |
| [完整 CRUD 分层案例](crud-example.md) | 实体、Repository、DTO、Service 和 Controller 配套使用。 |
| [Admin RBAC 与 JWT 登录案例](rbac.md) | 两表角色权限模型、统一 loginByUsername 与业务认证、无状态 JWT 语义。 |

## 建议顺序

[项目开发规范](conventions.md) → [公共实体与页面参数](models.md) → [通用 Service 与树服务](service.md)。已有明确问题时直接选择对应小专题，不必按顺序读完。

只维护小专题正文；本页只提供导航。给 AI 使用时先提供 [llms.txt](../../llms.txt)，再按任务加载相关页面。
