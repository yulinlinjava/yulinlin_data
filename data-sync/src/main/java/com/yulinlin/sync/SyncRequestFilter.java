package com.yulinlin.sync;

import com.yulinlin.data.core.filter.IWriteFilter;
import com.yulinlin.data.core.request.ExecuteRequest;

/** RouteSession 的实际写后通知，不在 queue() 接收请求时触发。 */
public final class SyncRequestFilter implements IWriteFilter {
    private final SyncManager manager;
    public SyncRequestFilter(SyncManager manager) { this.manager = manager; }
    @Override public ExecuteRequest<?> beforeWrite(String session, ExecuteRequest<?> request) {
        String source = request.getSession();
        manager.validateWrite(source == null || source.isBlank() ? session : source, request);
        return request;
    }
    @Override public void afterWrite(String session, ExecuteRequest<?> request, int affectedRows) {
        manager.capture(session, request);
    }
    @Override public int order() { return Integer.MAX_VALUE; }
}
