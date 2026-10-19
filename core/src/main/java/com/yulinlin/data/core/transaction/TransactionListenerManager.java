package com.yulinlin.data.core.transaction;

import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.session.EntitySession;
import lombok.SneakyThrows;

import java.util.*;
import java.util.function.Supplier;

/** 同一线程只有一个业务事务上下文；Session 加入事务时只开启自己的资源。 */
public class TransactionListenerManager {
    private static final ThreadLocal<TransactionContext> CURRENT = new ThreadLocal<>();
    private final List<TransactionListener> list;
    private volatile boolean transactionLog;

    public TransactionListenerManager() { this(List.of()); }
    public TransactionListenerManager(List<TransactionListener> list) { this.list = new ArrayList<>(list); }
    public static TransactionContext currentContext() { return CURRENT.get(); }
    public boolean isTransactionLog() { return transactionLog; }
    public void setTransactionLog(boolean transactionLog) { this.transactionLog = transactionLog; }

    public TransactionListenerManager add(TransactionListener listener) { list.add(listener); return this; }
    public TransactionListenerManager addAll(List<? extends TransactionListener> listeners) {
        list.addAll(listeners);
        return this;
    }

    public void startTransaction() {
        TransactionContext context = CURRENT.get();
        if (context != null) {
            if (context.closing) throw new IllegalStateException("当前事务已进入提交或回滚阶段");
            context.depth++;
            logState(context, "嵌套事务开启");
            return;
        }
        context = new TransactionContext(this);
        CURRENT.set(context);
        logState(context, "事务开启");
        try {
            for (TransactionListener listener : list) listener.startTransaction();
        } catch (Throwable error) {
            logError(context, "事务开启失败", error);
            context.fail(error);
            finish(context, true);
        }
    }

    /** 自动加入不增加业务嵌套深度，同一个 Session 的资源只开启一次。 */
    @SneakyThrows
    public EntitySession enlist(EntitySession session) {
        TransactionContext context = CURRENT.get();
        if (context == null) return session;
        if (context.participants.add(session)) {
            try {
                session.startTransaction();
                logSession(context, session, "事务加入数据源");
            }
            catch (Throwable error) {
                logError(context, "事务加入数据源失败", error);
                setRollbackOnly();
                throw error;
            }
        }
        return session;
    }

    public EntitySession resolveSession(Object router, String key, Supplier<EntitySession> supplier) {
        TransactionContext context = CURRENT.get();
        if (context == null) return supplier.get();
        return context.selections.computeIfAbsent(router, ignored -> new HashMap<>()).computeIfAbsent(key, ignored -> supplier.get());
    }

    public void clearSelections(Object router) {
        TransactionContext context = CURRENT.get();
        if (context != null) context.selections.remove(router);
    }

    public boolean isOpenTransaction() { return CURRENT.get() != null; }
    public int transactionDepth() { TransactionContext context = CURRENT.get(); return context == null ? 0 : context.depth; }

    public boolean isRollbackOnly() {
        TransactionContext context = CURRENT.get();
        return context != null && (context.rollbackOnly
                || context.participants.stream().anyMatch(EntitySession::isRollbackOnly));
    }

    public void setRollbackOnly() {
        TransactionContext context = CURRENT.get();
        if (context == null) return;
        boolean first = !context.rollbackOnly;
        context.rollbackOnly = true;
        if (first) logState(context, "事务已标记为只能回滚");
        for (EntitySession session : context.participants) context.attempt(session::setRollbackOnly);
    }

    public void commitTransaction() {
        TransactionContext context = CURRENT.get();
        if (context == null) return;
        if (context.depth > 1) {
            context.depth--;
            logState(context, "内层事务提交返回，等待最外层提交");
            return;
        }
        context.manager.finish(context, false);
    }

    public void rollbackTransaction() {
        TransactionContext context = CURRENT.get();
        if (context == null) return;
        setRollbackOnly();
        if (context.depth > 1) {
            context.depth--;
            logState(context, "内层事务回滚返回，最外层将回滚");
            return;
        }
        context.manager.finish(context, true);
    }

    public void afterCommit(Runnable action) {
        Objects.requireNonNull(action, "action");
        TransactionContext context = CURRENT.get();
        if (context == null || context.beforeCommitDone || context.completed)
            throw new IllegalStateException("afterCommit 需要活动事务，请先开启框架事务");
        context.afterCommitTasks.add(action);
    }

    public void invalidate(QueryCache cache, TransactionContext.CacheInvalidation invalidation) {
        if (invalidation == null || invalidation.empty()) return;
        TransactionContext context = CURRENT.get();
        if (context == null) invalidation.apply(cache);
        else context.invalidations.computeIfAbsent(cache, ignored -> new TransactionContext.CacheInvalidation()).merge(invalidation);
    }

    private void beforeCommit(TransactionContext context) {
        if (context.beforeCommitDone) return;
        logState(context, "事务提交前处理开始");
        for (TransactionListener listener : list) listener.commitTransaction();
        if (context.proxyChanges() != null) context.proxyChanges().flush();
        context.beforeCommitDone = true;
        logState(context, "事务提交前处理完成");
    }

    private void finish(TransactionContext context, boolean requestedRollback) {
        context.closing = true;
        logState(context, requestedRollback ? "事务请求回滚" : "事务请求提交");
        try {
            boolean rollback = requestedRollback || isRollbackOnly();
            if (!rollback) beforeCommit(context);
            rollback |= isRollbackOnly();
            if (rollback) {
                rollbackResources(context);
                if (!requestedRollback && context.failure == null)
                    context.fail(new IllegalStateException("事务已标记 rollback-only，不能提交"));
            } else {
                context.commitAttempted = true;
                for (EntitySession session : context.participants) {
                    logSession(context, session, "数据源事务提交开始");
                    session.commitTransaction();
                    logSession(context, session, "数据源事务提交方法已返回");
                }
                context.committed = true;
                logState(context, "框架事务资源处理完成");
            }
        } catch (Throwable error) {
            logError(context, "事务处理失败，尝试回滚尚未提交的资源", error);
            context.fail(error);
            rollbackResources(context);
        } finally {
            complete(context);
        }
        if (context.failure != null) logError(context, "事务结束存在异常，请核对各连接提交结果", context.failure);
        context.throwIfFailed();
    }

    private void rollbackResources(TransactionContext context) {
        logState(context, "事务资源回滚开始");
        context.rollbackOnly = true;
        for (TransactionListener listener : list) context.attempt(listener::rollbackTransaction);
        if (context.proxyChanges() != null) context.attempt(context.proxyChanges()::rollback);
        for (EntitySession session : context.participants) context.attempt(() -> {
            logSession(context, session, "数据源事务回滚开始");
            session.rollbackTransaction();
            logSession(context, session, "数据源事务回滚方法已返回");
        });
        logState(context, "事务资源回滚处理结束");
    }

    private void complete(TransactionContext context) {
        context.depth = 0;
        if (context.proxyChanges() != null) context.attempt(context.proxyChanges()::close);
        context.participants.clear();
        context.selections.clear();
        if (CURRENT.get() == context) CURRENT.remove();
        for (TransactionListener listener : list) context.attempt(listener::afterCompletion);
        context.completed = true;
        logState(context, "框架事务上下文已清理");
        context.attempt(() -> notifyIfReady(context));
    }

    private void notifyIfReady(TransactionContext context) {
        if (!context.completed || context.notified) return;
        context.notified = true;
        if (context.commitAttempted) context.invalidations.forEach((cache, invalidation) -> context.attempt(() -> invalidation.apply(cache)));
        context.invalidations.clear();
        List<Runnable> tasks = new ArrayList<>(context.afterCommitTasks);
        context.afterCommitTasks.clear();
        if (context.committed) {
            logState(context, "事务提交后回调开始");
            for (TransactionListener listener : list) context.attempt(listener::afterCommit);
            tasks.forEach(context::attempt);
            logState(context, "事务提交后回调处理结束");
            context.throwIfFailed();
        }
    }

    private static String logDetails(TransactionContext context) {
        return "depth=" + context.depth + " participants=" + context.participants.size()
                + " rollbackOnly=" + context.rollbackOnly;
    }

    private static void logState(TransactionContext context, String event) {
        if (context.transactionLog().isEnabled()) context.transactionLog().info(event, logDetails(context));
    }

    private static void logError(TransactionContext context, String event, Throwable error) {
        if (context.transactionLog().isEnabled()) context.transactionLog().error(event, logDetails(context), error);
    }

    private static void logSession(TransactionContext context, EntitySession session, String event) {
        if (!context.transactionLog().isEnabled()) return;
        context.transactionLog().info(event, logDetails(context)
                + " group=" + TransactionLog.token(session.group())
                + " cluster=" + TransactionLog.token(session.cluster())
                + " session=" + session.getClass().getSimpleName());
    }

    /** 保留固定监听器的显式通知接口；业务事务由 commitTransaction 统一协调。 */
    public void afterCommit() { notifyListeners(true); }
    public void afterCompletion() { notifyListeners(false); }

    private void notifyListeners(boolean committed) {
        Throwable failure = null;
        for (TransactionListener listener : list) {
            try { if (committed) listener.afterCommit(); else listener.afterCompletion(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }
}
