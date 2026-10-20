package com.yulinlin.data.core.session;

import com.yulinlin.data.core.parse.ParseResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 一次数据源执行结果，不代表事务已提交。耗时不包含日志与监听器自身。
 * requests 是只读列表快照，内部请求只供同步观察，不得修改或长期持有。
 */
public record SessionExecution(
        EntitySession session,
        List<ParseResult> requests,
        long elapsedNanos,
        Integer affectedRows,
        Throwable error,
        boolean batch
) {
    public SessionExecution {
        Objects.requireNonNull(session, "session");
        requests = List.copyOf(requests);
        if (elapsedNanos < 0) throw new IllegalArgumentException("elapsedNanos must not be negative");
    }

    public long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
    }
}
