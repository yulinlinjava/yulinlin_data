# 线程、调度与事件

[文档首页](../README.md) / [常用工具](README.md)

锁、虚拟线程、延迟调度和进程内事件。

## 本页导航

- [并发与调度](#并发与调度)
- [SegmentLock](#segmentlock)
- [ThreadUtil](#threadutil)
- [进程内事件](#进程内事件)

## 并发与调度

### SegmentLock

```java
SegmentLock locks = new SegmentLock(64);
locks.execute(userId, () -> updateUser(userId));
Order order = locks.executeWithReturn(orderId, () -> loadOrder(orderId));
```

相同 key 一定进入同一段，不同 key 可能碰撞到同一锁。段数创建后固定；构造参数必须大于 0。它适合短临界区，不提供跨进程锁。

### ThreadUtil

```java
Future<Result> ioFuture = ThreadUtil.submit(this::blockingHttpOrJdbcCall);
Future<Result> explicitIo = ThreadUtil.submitVirtual(this::blockingHttpOrJdbcCall);
Future<Result> cpuFuture = ThreadUtil.submitCpu(this::calculate);
ThreadUtil.submit(this::refresh, 500);       // 延迟约 500 ms 执行一次
ScheduledFuture<?> task = ThreadUtil.schedule(this::blockingPoll, 1_000);
ScheduledFuture<?> cpuTask = ThreadUtil.scheduleCpu(this::calculate, 1_000);

ThreadUtil.set("trace", traceId);
try {
    useTrace(ThreadUtil.get("trace"));
} finally {
    ThreadUtil.clear();
}
```

`ThreadUtil.submit` 默认使用 JDK 25 一任务一虚拟线程；`submitVirtual` 是语义更明确的同义入口，适合 HTTP、文件、网络、等待锁等阻塞任务。CPU 密集计算或必须由线程池限制并发的任务使用 `submitCpu`，其平台线程数按可用处理器数量固定。虚拟线程不会让单次计算更快，也不会自动限制数据库连接或远端接口并发，因此 ORM 批量写入仍使用 Session 的连接并发配置。

`schedule` 与 `scheduleVirtual` 都由轻量调度器触发、在虚拟线程执行，并在上一次尚未完成时跳过重叠执行；CPU 定时任务使用 `scheduleCpu`。`submitVirtual(task, delay)` 是一次性延迟虚拟任务。`startThread` 使用虚拟线程循环执行，回调返回 `false`、线程被中断时停止。ThreadLocal 数据必须在 finally 中 clear。Spring 应用有复杂隔离、监控或优雅停机要求时仍可注入自己的 `TaskExecutor`/`TaskScheduler`。

## 进程内事件

```java
final class UserCreated { }

final class UserCreatedHandler implements IEventHandler<UserCreated> {
    public void handle(UserCreated event) { /* ... */ }
}

EventPublishManager.instance().register(new UserCreatedHandler());
EventPublishManager.instance().publish(new UserCreated());
EventPublishManager.instance().asyncPublish(new UserCreated());
```

`IEvent` 提供 `publish`/`asyncPublish` 默认方法。处理器按事件的精确运行时 Class 分组，不向父类或接口传播；重复 register 会重复执行。注册表支持并发读写，适合启动期间注册、运行期间高频发布。同步发布的异常直接中断后续处理器；异步发布通过 `ThreadUtil` 使用虚拟线程，不返回 Future，调用方无法等待，也不会自动汇总错误。需要可靠投递、持久化、重试或跨进程通信时使用消息队列。

自定义隔离实例可以继承 `AbstractEventPublishManager<MyManager>`；全局默认实例是 `EventPublishManager.instance()`。

---

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
