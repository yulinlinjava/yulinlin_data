# SQLite/H2 十万行写入基准

这是 JMH 宏基准：一次 `op` 默认由 4 个业务线程共同写入 100,000 行；每个线程循环提交独立 ORM 请求，每个请求最多 128 个对象。它不属于普通单元测试，默认 Maven 构建也不会编译或运行；只有启用 `local-db-benchmark` Profile 才加入 `src/jmh/java`、生成 JMH 代码和 benchmark JAR。

本机完整实测结果见 [LOCAL_DB_BENCHMARK_REPORT.md](LOCAL_DB_BENCHMARK_REPORT.md)。

## 运行

在仓库根目录使用 JDK 25 和 Maven：

```powershell
./admin/run-local-db-benchmark.ps1
```

调整规模或 JMH 轮次：

```powershell
./admin/run-local-db-benchmark.ps1 -Size 100000 -BusinessThreads 4 -RequestSize 128 `
  -Forks 2 -Warmups 3 -Iterations 5 -Duration 2s -MaxHeap 2g
```

脚本先执行：

```powershell
mvn -pl admin -am '-Dmaven.test.skip=true' '-Plocal-db-benchmark' package
```

随后运行 `admin/target/admin-3.0-benchmarks.jar`。文本和 JSON 原始结果保存到 `admin/target/local-db-jmh/sqlite-h2-business-threads.*`。

## 两组结果

| Benchmark | 含义 |
| --- | --- |
| sqliteConcurrentRequests | 4 个业务线程并发提交；SQLite 物理连接池固定为 1，写请求排队执行 |
| h2ConcurrentRequests | 4 个业务线程并发提交；H2 连接池最多提供 4 个写连接 |

两组使用同一个实体、相同的 100,000 行输入和一个普通联合索引。默认会生成 782 个独立请求，其中 781 个请求为 128 行、最后一个为 32 行；请求按轮询分配给 4 个长期存活的业务线程。每个请求小于 256 行 JDBC `executeBatch` 上限，因此一次请求只执行一次 JDBC batch，且不会调用框架内部 `.batch()` 再拆连接。每次计时前清空对应表，计时后查询并断言行数；清空、校验、Spring 启动、数据生成、请求分组和首次建表/建索引均不计入分数。数据库文件位于每个 JMH Trial 的临时目录，结束后关闭上下文并删除。

JMH 输出的 `ms/op` 表示写入整批数据的平均耗时，不是单行耗时。吞吐量可按 `size / (ms/op / 1000)` 换算。GC profiler 同时报告每批分配量和 GC 数据。

## 解读限制

- SQLite 使用模块默认 WAL + synchronous=NORMAL；H2 使用文件模式和 MySQL 兼容模式，两者持久化策略不完全等价。
- 每个 128 行请求是独立事务，10 万行整体不是一个原子事务；这与外层多线程业务一致。
- 结果会明显受磁盘、杀毒软件、系统文件缓存、CPU 电源策略和其他负载影响。
- 这是当前 ORM 完整编码、SQL 解析和写入路径的结果，不等于数据库原生 JDBC 极限。
- 比较报告应同时保存 JDK、硬件、文件系统、参数、JSON 和代码版本，不只记录“谁最快”。
