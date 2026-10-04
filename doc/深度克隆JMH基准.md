# 深度克隆 JMH 基准

基准源码：`admin/src/main/java/com/yulinlin/admin/DeepCloneBenchmark.java`（保留当前项目中的位置）。
这是独立运行的 JMH 性能基准，不是由 JUnit/Surefire 执行的单元测试。因为当前基准位于主源码目录，编译 admin 时需要启用 `jmh` profile；不建议将该性能测试模块的产物用于生产部署。

## 构建和运行

在项目根目录执行，确保 `java -version` 和 `mvn -version` 都使用 JDK 25，并已安装 Maven：

```powershell
mvn -pl admin -am -Pjmh -DskipTests clean package
java -jar admin/target/admin-3.0-benchmarks.jar DeepCloneBenchmark -prof gc -rf json -rff admin/target/deep-clone-jmh.json
```

首次构建需要下载 JMH 和构建插件。只在性能测试时启用 `jmh` profile，不要用此 profile 发布业务依赖。随后进行普通构建时使用 `clean`，避免残留基准类。

在 IDEA 中运行：在 Maven 面板启用 `jmh` profile 并重新加载项目，再用 Run 运行 `DeepCloneBenchmark.main()`，不要使用 Debug。需要开启注解处理以生成 JMH 的 `META-INF/BenchmarkList`；若提示找不到基准列表或 fork 类路径异常，请使用上面的 Maven 打包及 `java -jar` 命令。基准不启动 Spring 容器，也不连接数据库。

本机无需 Maven 的复现方式（使用已有依赖缓存和 `lang/target/classes`，重新编译当前反射实现与基准）：

```powershell
./admin/run-clone-benchmark.ps1 -Repository E:/maven
```

脚本默认每轮 2 秒，结果写入 `admin/target/clone-jmh/deep-clone-comparison.json` 和同名 `.log`。需要 JDK 25 的 `java`、`javac` 均在 PATH 中；依赖缺失时脚本会报错，不会自动下载。

默认每批 **200,000 个对象**、单线程、两个独立 JVM fork，每个方法在每个 fork 中预热 3 轮、测量 5 轮，每轮目标时间 1 秒，fork 堆内存固定为 1 GiB。实际轮次时长可能超过 1 秒，因为一次批量克隆必须完整执行。运行时尽量关闭其他高负载程序。

先快速验证运行链路（不能用于正式性能结论）：

```powershell
java -jar admin/target/admin-3.0-benchmarks.jar DeepCloneBenchmark -p size=1000 -f 1 -wi 1 -i 1 -w 1s -r 1s
```

更稳定的长时间测量：

```powershell
java -jar admin/target/admin-3.0-benchmarks.jar DeepCloneBenchmark -p size=200000 -wi 5 -i 10 -w 3s -r 3s -f 3 -prof gc -rf json -rff admin/target/deep-clone-jmh.json
```

## 测什么

| 方法 | 每次操作 |
| --- | --- |
| `wholeList` | 对整个列表调用一次 `ReflectionUtil.deepClone(source)` |
| `perUser` | 遍历列表，每个对象调用一次 `deepClone`，加入新列表 |
| `kryoWholeList` | Kryo 5.6.2 整体深克隆，启用引用跟踪 |
| `kryoPerUser` | 同一线程复用 Kryo 实例，逐条深克隆 |
| `serializationWholeList` | Commons Lang 3.17.0 Java 序列化整体深克隆 |
| `serializationPerUser` | Commons Lang 逐条序列化深克隆 |
| `manualPerUser` | 针对当前字段结构手写深克隆，仅作为专用基线 |

Kryo 使用默认序列化器、不要求注册类，实例在线程级 Trial setup 中创建。第三方依赖只加在 admin 的 jmh profile，未改变 lang 的生产依赖。模型实现 Serializable 以支持 Apache 方案；手写基线只适用当前固定字段和引用结构，不能替代通用图克隆。全部方案在 setup 中检查嵌套对象独立性、字段值及地址共享引用。

每个对象包含基本类型、字符串、嵌套地址、字符串列表、Map、整数数组。Map 引用该对象的同一个地址，检验克隆后内部共享引用是否保留。不同用户之间没有共享可变对象，使这两种写法在本数据集上语义一致。

注意：如果真实数据中不同用户共享可变对象，整体克隆会保留这种共享关系；逐条克隆不会跨调用保留共享关系。不能仅凭耗时互相替换。

数据生成和小样本正确性检查放在 Trial setup 中，不计入正式计时。基准返回结果，由 JMH 消费，避免无用结果被优化。计时包含克隆结果列表及嵌套对象分配；不主动触发 `System.gc()`，GC 成本属于实际分配负担。完整正确性回归保留在 lang 模块的 `src/test` 下。

## 怎么看结果

- 主结果 `Score` 的单位为 **ms/op**：克隆整批数据的平均耗时，越小越好，不是单个对象耗时，也不是中位数。
- `Error` 是测量误差区间的半宽；误差很大时增加测量时间，并检查机器负载。
- `gc.alloc.rate.norm` 的单位为 **B/op**：每批克隆分配的字节数，不是存活堆大小。
- `gc.count`、`gc.time` 用于观察测量期间的垃圾回收负担。
- 换算处理速度：`size × 1000 / Score` 个对象/秒。默认 `size=200000`。

参数 `-p size=1000,10000,200000` 可比较不同数据规模。结论仅适用于当前 JDK、硬件、堆设置和对象结构，不代表所有业务对象性能。正确性回归仍由 `src/test` 中的测试负责；不要添加固定毫秒数的通过/失败断言。
