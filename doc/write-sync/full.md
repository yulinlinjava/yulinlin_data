# 人工全量同步与续传

[文档首页](../README.md) / [批量与同步](README.md)

全量由人工调用，基于主键进度续传；操作前必须业务停写。

前置配置与失败恢复见[增量同步](incremental.md)。这里是人工全量，不是业务持续写入期间的无缝快照同步。

## 本页导航

- [全量 API：自动读取主键进度](#全量-api自动读取主键进度)
- [人工全量流程：业务停写，内部暂停增量](#人工全量流程业务停写内部暂停增量)

## 全量 API：自动读取主键进度

完成下面的停写、排空步骤后，注入 `com.yulinlin.sync.DataSyncClient` 调用；无需自己查询最后一条记录或记录主键。人工 API 不要求实体命中增量 `entities`，调用方显式传入实体类，并自行确认权限和迁移范围：

```java
import com.yulinlin.sync.DataSyncClient;
import com.yulinlin.sync.SyncResult;
import org.springframework.stereotype.Service;

@Service
public class VideoSyncService {
    private final DataSyncClient dataSyncClient;

    public VideoSyncService(DataSyncClient dataSyncClient) {
        this.dataSyncClient = dataSyncClient;
    }

    // 前提：已停写、在途事务结束、相关增量队列排空。
    // 不加 @Transactional；人工触发后台维护任务，方法阻塞到完成或失败。
    public SyncResult syncVideos() {
        return dataSyncClient.sync("mysql", "postgresql", VideoEntity.class);
    }

    // 明确开启新一轮：从头复制，但不清空目标表。
    public SyncResult restartVideos() {
        return dataSyncClient.restart("mysql", "postgresql", VideoEntity.class);
    }
}
```

仅用人工 API 时可以不配置增量规则：

```yaml
yulinlin:
  sync:
    enabled: true
    full-batch-size: 1000
    rules: []
```

| 调用 / 状态 | 行为 |
| --- | --- |
| 首次 `sync(source, target, class)` | 主键升序，从第一批开始 |
| 中断或失败后再次 `sync(...)` | 保持停写且源数据未变化时，从 `主键 > 最后成功主键` 继续，不使用 OFFSET；若已恢复写入，先核对是否需 `restart` |
| 已完成后再次 `sync(...)` | 返回已有完成结果，本次复制数为 0；不会自动追加源库新增数据 |
| `restart(...)` | 新任务 ID、清零该任务进度，从头按主键新增或覆盖；不删除目标额外数据 |
| 映射、主键结构或实际连接指纹变化 | 拒绝沿用旧进度，核对后使用 `restart` |
| 检测到全量期间源写入 | 暂停相关增量规则，全量进度标记 `RESTART_REQUIRED`；停写、人工修复并排空规则后 `restart`，不能直接续传 |

`SyncResult` 返回 `taskId/source/target/entity/status/lastPrimaryKey/completedRows/rowsThisRun/batchesThisRun/elapsedMillis`。`completedRows` 是本轮累计复制的源行数，`rowsThisRun` 是这次调用确认提交的源行数，不是数据库 UPDATE 的 changed-rows；覆盖相同值也计入复制数。失败会抛异常，并保留可恢复的检查点。

**主键与字段要求：**

- 使用完整单表 `@JoinTable` 实体，主键声明 `@JoinMeta(primaryKey = true)`，且与两端实际单列主键一致；不要求实体 `autoSchema=true`。
- 主键支持 `String`、byte/short/int/long 及其包装类、`BigInteger`；不支持复合主键、浮点主键、UUID Java 类型。主键不能为空，需保持稳定；字符串按源数据库排序，不执行数字“+1”。
- 复制实体声明的持久化字段，包括 `null`、`update=false`、ID 和时间；跳过 `exist=false`、计算字段、关联加载字段。使用源 CoderManager 解码、目标 CoderManager 编码，复用对应 Session 的值读取/绑定策略。
- 拒绝聚合投影和 JoinTableList 联表实体。不经过业务过滤器、代理、自动补时间或增量回放；不能依赖租户过滤器限制迁移范围，也不支持动态分表。应由有权限的维护代码调用，模块不公开 HTTP 管理接口。
- 仅支持 MySQL/PostgreSQL。目标 MySQL 业务表与进度表必须是 InnoDB；触发器、生成列、自增序列、额外唯一约束、字段范围和排序规则需人工确认。复制显式主键不会自动调整 PostgreSQL sequence；序列型主键后续插入前应自行校准。

**进度、事务和日志：**

进度存入目标连接默认库/schema 的 `yulinlin_sync_progress`，任务按源 group + 目标 group + 实体类区分；每个任务只保存最新一轮进度，不保存所有历史轮次。需要该表的建表和读写权限，业务表需 SELECT/INSERT/UPDATE 权限。每批数据和检查点使用同一目标连接提交，失败回滚本批；已提交批次不回滚。目标按主键区分 INSERT/UPDATE，不使用 MySQL 任意唯一键冲突覆盖。每批提交后主动清理目标实体缓存。

运行全量时持有源、目标各一个维护连接；目标事务按批提交，不使用多连接批处理。相同任务通过目标库会话锁防止重复运行。进程异常退出后可续传，连接断开后的锁释放以数据库为准；持久化 RUNNING 不代表仍有工作线程。不要同时用不同 group 别名/不同实体启动对同一物理表的全量任务。

日志示例（数值仅示意）：

```text
全量同步开始 源=mysql 目标=postgresql 实体=com.example.VideoEntity 任务=... 每批=1000 恢复主键=2000 累计完成=2000
全量同步进度 源=mysql 目标=postgresql 实体=com.example.VideoEntity 本批=1000 本次完成=1000 累计完成=3000 最后成功主键=3000 本批耗时=120ms
全量同步完成 源=mysql 目标=postgresql 实体=com.example.VideoEntity 本次完成=1000 累计完成=3000 批次=1 总耗时=135ms
```

不预先 COUNT 全表，所以不提供百分比。异常日志提示排查和续传；提交确认异常时，以目标进度表实际值为准，不用旧内存游标覆盖已提交进度。

## 人工全量流程：业务停写，内部暂停增量

按下面的顺序操作，无需业务再写一套暂停/恢复增量消费者的代码：

1. **业务停写**：关闭相关源/目标业务写入口，覆盖所有应用实例、定时任务及其他程序的写入。先停止产生新任务，再让 EntityCounter、`.queue()` 和在途事务处理完；不能只暂停 HTTP 接口。
2. **排空增量**：保持增量消费正常运行，等待提交后的同步任务完成。用 `syncManager.statuses()` 核对写向本次目标组的规则无积压、无错误。不要先关闭整个同步模块，否则不能排空。
3. **人工调用**：在业务事务之外调用 `sync(source, target, class)`，需要重新覆盖全部历史数据时调用 `restart(...)`。API 会再次检查规则是否空闲；尚在执行、积压、已暂停或被其他全量占用时直接报错，不自动等待、不丢弃任务。状态计数只是观察入口，最终以 API 检查为准。
4. **内部暂停**：检查通过后，本进程所有写向该目标 group 的增量规则进入维护保护，不再调度消费；不只是当前实体或当前源组。随后逐批复制，业务行与检查点同批提交。
5. **完成并恢复**：API 正常返回时释放内部维护保护，未处于错误暂停状态的规则恢复正常调度。核对结果、日志和规则状态后，再由业务恢复写入。正常全量完成不需要调用 `resumeAfterRepair`。

**内部暂停不等于业务停写。** 不会拦截用户请求、不撤销源事务，也不会停止其他应用实例、原生 SQL 或数据库客户端的修改。若其他源组的规则也写向这个目标 group，同样需要协调它们的停写。目标读取未被禁止，但全量按批提交，期间可能看到部分新、部分旧的数据，不能把它当作整表原子切换。

| 情况 | 内部处理 | 业务操作 |
| --- | --- | --- |
| 全量正常完成 | 释放维护保护，正常规则恢复调度 | 核对后恢复写入 |
| 普通读取/写入失败，没有源写入冲突 | 回滚未提交批次，保留已提交进度；退出时释放维护保护，正常规则可再次调度 | 保持停写，排查后续传；不能因为消费者已恢复就认定全量成功 |
| 全量期间检测到匹配规则的源写入或提交 | 标记冲突、暂停相关增量规则；全量任务处理中检测到冲突会中止并记录需要重做 | 保持停写，人工核对、修复和处理积压，再 `restart`；不要盲目重放 inc/dec |
| 进程退出 | 全量检查点已落在目标库；内存增量任务和运行状态不保证保留 | 重启后先确认数据及缺口，不把“队列空了”当作已同步成功 |

失败恢复期间也需保持停写；如果期间已恢复写入，旧游标之前的数据可能变化，需要核对并重新全量。全量不会删除目标额外记录，漏同步的删除仍需人工清理。正常匹配规则之外、其他进程或原生 SQL 的修改可能检测不到，因此这**不是不停机快照与 CDC 无缝衔接**。

启动自动全量及其扩展入口已移除；升级时删除旧配置中的 `rules[].full`，不再填写 true/false。`full-batch-size` 仍保留，用于人工全量 API 每批读取/提交数量，不是自动全量开关。

---

下一步：返回[批量与同步主题目录](README.md)按需选择相关专题。
