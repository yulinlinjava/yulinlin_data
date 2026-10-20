package com.yulinlin.sync;

import com.yulinlin.data.core.filter.IRequestFilter;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;

/** RouteSession 的实际写后通知，不在 queue() 接收请求时触发。 */
public final class SyncRequestFilter implements IRequestFilter {
    private final SyncManager manager;
    public SyncRequestFilter(SyncManager manager) { this.manager = manager; }
    @Override public Object updateAfter(String session, BaseRequest<?> request, Object result) {
        manager.capture(session, (ExecuteRequest<?>) request);
        return result;
    }
    @Override public int order() { return Integer.MAX_VALUE; }
}
