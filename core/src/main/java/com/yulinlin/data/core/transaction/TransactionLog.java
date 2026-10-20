package com.yulinlin.data.core.transaction;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/** 只记录诊断信息，不参与事务决策；事务 ID 始终保留，开关仅控制生命周期日志和计时。 */
@Slf4j
public final class TransactionLog {
    private static final AtomicLong IDS = new AtomicLong();
    private static final TransactionLog NONE = new TransactionLog(false, "none");

    private final boolean enabled;
    private final String id;
    private final long started;

    private TransactionLog(boolean enabled, String id) {
        this.enabled = enabled;
        this.id = id;
        started = enabled ? System.nanoTime() : 0;
    }

    static TransactionLog start(boolean enabled) {
        return new TransactionLog(enabled, Long.toHexString(IDS.incrementAndGet()));
    }

    /** 在所属线程捕获，JDBC 事务资源可将此只读日志对象带到批处理线程。 */
    public static TransactionLog current() {
        TransactionContext context = TransactionListenerManager.currentContext();
        return context == null ? NONE : context.transactionLog();
    }

    public boolean isEnabled() { return enabled; }

    /** 当前进程内的框架事务 ID；没有框架事务时为 none。 */
    public String id() { return id; }

    public void info(String event, String details) {
        if (!enabled) return;
        try {
            log.info("{} txId={} elapsedMs={} {}", event, id, elapsedMs(), details);
        } catch (RuntimeException ignored) {
            // 日志输出失败不能改变数据库提交、回滚和异常传播。
        }
    }

    public void warn(String event, String details) {
        if (!enabled) return;
        try {
            log.warn("{} txId={} elapsedMs={} {}", event, id, elapsedMs(), details);
        } catch (RuntimeException ignored) {
            // 诊断日志不影响业务。
        }
    }

    public void error(String event, String details, Throwable error) {
        if (!enabled) return;
        try {
            log.error("{} txId={} elapsedMs={} {}", event, id, elapsedMs(), details, error);
        } catch (RuntimeException ignored) {
            // 保留原始事务异常，不被日志异常覆盖。
        }
    }

    private long elapsedMs() { return (System.nanoTime() - started) / 1_000_000; }

    public static String token(Object value) {
        return String.valueOf(value).replace('\r', ' ').replace('\n', ' ');
    }
}
