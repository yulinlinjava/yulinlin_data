package com.yulinlin.sync;

import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import com.yulinlin.jdbc.session.JdbcSession;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.function.Supplier;

/** 人工全表补同步，阻塞执行；调用方需停写并安排在后台维护任务中。 */
public final class DataSyncClient {
    private final Supplier<RouteSession> routes;
    private final SyncManager manager;
    private final int batchSize;

    public DataSyncClient(Supplier<RouteSession> routes, SyncManager manager, DataSyncProperties properties) {
        this.routes = Objects.requireNonNull(routes);
        this.manager = Objects.requireNonNull(manager);
        batchSize = properties.getFullBatchSize();
        if (batchSize < 1 || batchSize > 10000)
            throw new IllegalArgumentException("yulinlin.sync.full-batch-size 必须在 1 到 10000 之间");
    }

    public SyncResult sync(String source, String target, Class<?> entity) {
        return run(source, target, entity, false);
    }

    /** 新一轮从头覆盖；不清空目标表，也不删除多余记录。 */
    public SyncResult restart(String source, String target, Class<?> entity) {
        return run(source, target, entity, true);
    }

    private SyncResult run(String sourceGroup, String targetGroup, Class<?> entity, boolean restart) {
        if (sourceGroup == null || sourceGroup.isBlank() || targetGroup == null || targetGroup.isBlank()
                || sourceGroup.equals(targetGroup) || entity == null || entity == Object.class)
            throw new IllegalArgumentException("请指定不同的源/目标 group 和完整实体类");
        if (TransactionListenerManager.currentContext() != null || TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("全量同步不能放在业务事务中，请在事务外后台执行");
        RouteSession route = routes.get();
        var from = route.session(sourceGroup, JoinCluster.master);
        var to = route.session(targetGroup, JoinCluster.master);
        if (!(from instanceof JdbcSession source) || !(to instanceof JdbcSession target))
            throw new IllegalArgumentException("全量同步当前只支持 JDBC MySQL/PostgreSQL Session");
        try (SyncManager.ManualGuard guard = manager.beginManual(targetGroup)) {
            return source.withMaintenanceConnection(input -> target.withMaintenanceConnection(output ->
                    new JdbcFullSyncJob(route, source, target, entity, batchSize, guard, restart).run(input, output)));
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("全量同步未完成，源=" + sourceGroup + "，目标=" + targetGroup
                    + "，实体=" + entity.getName() + "；请核对日志，修复后调用 sync 读取已提交进度继续", error);
        }
    }
}
