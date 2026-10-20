package com.yulinlin.monitor;

import com.yulinlin.data.core.session.SessionExecution;
import com.yulinlin.data.core.session.SessionExecutionListener;

/** 直接观察 Session 执行结果，SQL 日志关闭不影响监控。当前只采集 JDBC 请求。 */
public final class MonitorExecutionListener implements SessionExecutionListener {
    private final DatabaseMonitor monitor;
    private final DatabaseOperationFactory factory = new DatabaseOperationFactory();

    public MonitorExecutionListener(DatabaseMonitor monitor) {
        this.monitor = java.util.Objects.requireNonNull(monitor, "monitor");
    }

    @Override
    public void afterExecution(SessionExecution execution) {
        int affected = execution.affectedRows() == null ? -1 : execution.affectedRows();
        boolean success = execution.error() == null;
        DatabaseOperation operation = execution.batch()
                ? factory.batch(execution.elapsedMillis(), execution.requests(), affected,
                        success, execution.error(), execution.session())
                : factory.single(execution.elapsedMillis(),
                        execution.requests().isEmpty() ? null : execution.requests().getFirst(),
                        affected, success, execution.error(), execution.session());
        // 其他数据源也可接收同一监听器，但当前监控工厂只理解 SqlNode。
        if (operation != null) monitor.record(operation);
    }
}
