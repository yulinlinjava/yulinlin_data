package com.yulinlin.monitor;

import com.yulinlin.data.core.log.LogPrint;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.jdbc.session.SqlNode;

import java.util.List;

/** Observes the existing JDBC log dispatch without adding a second execution interceptor. */
public final class MonitorDatabaseLog implements LogPrint<SqlNode> {
    private final DatabaseMonitor monitor;
    private final DatabaseOperationFactory factory = new DatabaseOperationFactory();

    public MonitorDatabaseLog(DatabaseMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void success(long elapsedMs, ParseResult result) {
        success(elapsedMs, result, null);
    }

    @Override
    public void success(long elapsedMs, ParseResult result, EntitySession session) {
        monitor.record(factory.single(elapsedMs, result, -1, true, null, session));
    }

    @Override
    public void error(Throwable error, ParseResult result) {
        error(-1, error, result, null);
    }

    @Override
    public void error(long elapsedMs, Throwable error, ParseResult result, EntitySession session) {
        monitor.record(factory.single(elapsedMs, result, -1, false, error, session));
    }

    @Override
    public void successBatch(long elapsedMs, List<ParseResult> results, int affected, EntitySession session) {
        monitor.record(factory.batch(elapsedMs, results, affected, true, null, session));
    }

    @Override
    public void errorBatch(long elapsedMs, Throwable error, List<ParseResult> results, EntitySession session) {
        monitor.record(factory.batch(elapsedMs, results, -1, false, error, session));
    }
}
