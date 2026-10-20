package com.yulinlin.data.core.session;

/**
 * 可选的数据源执行观察接口，与日志开关无关。
 * 可能在批量工作线程并发调用；实现应线程安全、轻量，不得修改请求或操作当前事务。
 * 仅观察执行结果，需要提交成功通知时使用 TransactionUtil.afterCommit。
 */
@FunctionalInterface
public interface SessionExecutionListener {
    void afterExecution(SessionExecution execution);
}
