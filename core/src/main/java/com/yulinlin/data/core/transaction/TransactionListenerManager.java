package com.yulinlin.data.core.transaction;

import com.yulinlin.data.core.cache.QueryCache;
import com.yulinlin.data.core.session.EntitySession;
import lombok.SneakyThrows;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Supplier;

/** 同一线程只有一个业务事务上下文；Session 加入事务时只开启自己的资源。 */
public class TransactionListenerManager {
    static final int NO_SPRING_TRANSACTION = -2;
    private static final int SPRING_PENDING = -1;
    private static final ThreadLocal<TransactionContext> CURRENT = new ThreadLocal<>();
    private final List<TransactionListener> list;

    public TransactionListenerManager() { this(List.of()); }
    public TransactionListenerManager(List<TransactionListener> list) { this.list = new ArrayList<>(list); }
    public static TransactionContext currentContext() { return CURRENT.get(); }

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
            return;
        }
        context = new TransactionContext(this);
        CURRENT.set(context);
        try {
            joinSpring(context);
            for (TransactionListener listener : list) listener.startTransaction();
        } catch (Throwable error) {
            context.fail(error);
            finish(context, true);
        }
    }

    /** 自动加入不增加业务嵌套深度，同一个 Session 的资源只开启一次。 */
    @SneakyThrows
    public EntitySession enlist(EntitySession session) {
        TransactionContext context = CURRENT.get();
        if (context == null) return session;
        context.manager.joinSpring(context);
        if (context.participants.add(session)) {
            try { session.startTransaction(); }
            catch (Throwable error) {
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
        context.rollbackOnly = true;
        for (EntitySession session : context.participants) context.attempt(session::setRollbackOnly);
    }

    public void commitTransaction() {
        TransactionContext context = CURRENT.get();
        if (context == null) return;
        if (context.depth > 1) { context.depth--; return; }
        context.manager.finish(context, false);
    }

    public void rollbackTransaction() {
        TransactionContext context = CURRENT.get();
        if (context == null) return;
        setRollbackOnly();
        if (context.depth > 1) { context.depth--; return; }
        context.manager.finish(context, true);
    }

    public void afterCommit(Runnable action) {
        Objects.requireNonNull(action, "action");
        TransactionContext context = CURRENT.get();
        if (context == null || context.beforeCommitDone || context.completed || context.springStatus >= 0)
            throw new IllegalStateException("afterCommit 需要活动事务，请先开启框架事务");
        context.manager.joinSpring(context);
        context.afterCommitTasks.add(action);
    }

    public void invalidate(QueryCache cache, TransactionContext.CacheInvalidation invalidation) {
        if (invalidation == null || invalidation.empty()) return;
        TransactionContext context = CURRENT.get();
        if (context == null) invalidation.apply(cache);
        else context.invalidations.computeIfAbsent(cache, ignored -> new TransactionContext.CacheInvalidation()).merge(invalidation);
    }

    /** 代理首次使用或 setter 时确认 Spring 提交边界，不再各自注册同步器。 */
    public void synchronize() {
        TransactionContext context = CURRENT.get();
        if (context != null) context.manager.joinSpring(context);
    }

    private void beforeCommit(TransactionContext context) {
        if (context.beforeCommitDone) return;
        for (TransactionListener listener : list) listener.commitTransaction();
        if (context.proxyChanges() != null) context.proxyChanges().flush();
        context.beforeCommitDone = true;
    }

    private void finish(TransactionContext context, boolean requestedRollback) {
        context.closing = true;
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
                for (EntitySession session : context.participants) session.commitTransaction();
                context.committed = true;
            }
        } catch (Throwable error) {
            context.fail(error);
            rollbackResources(context);
        } finally {
            complete(context);
        }
        context.throwIfFailed();
    }

    private void rollbackResources(TransactionContext context) {
        context.rollbackOnly = true;
        for (TransactionListener listener : list) context.attempt(listener::rollbackTransaction);
        if (context.proxyChanges() != null) context.attempt(context.proxyChanges()::rollback);
        for (EntitySession session : context.participants) context.attempt(session::rollbackTransaction);
    }

    private void complete(TransactionContext context) {
        context.depth = 0;
        if (context.proxyChanges() != null) context.attempt(context.proxyChanges()::close);
        context.participants.clear();
        context.selections.clear();
        if (CURRENT.get() == context) CURRENT.remove();
        for (TransactionListener listener : list) context.attempt(listener::afterCompletion);
        context.completed = true;
        context.attempt(() -> notifyIfReady(context));
    }

    private void notifyIfReady(TransactionContext context) {
        if (!context.completed || context.notified) return;
        if (context.committed && context.springStatus == SPRING_PENDING) return;
        context.notified = true;
        if (context.commitAttempted) context.invalidations.forEach((cache, invalidation) -> context.attempt(() -> invalidation.apply(cache)));
        context.invalidations.clear();
        List<Runnable> tasks = new ArrayList<>(context.afterCommitTasks);
        context.afterCommitTasks.clear();
        if (context.committed && (context.springStatus == NO_SPRING_TRANSACTION
                || context.springStatus == TransactionSynchronization.STATUS_COMMITTED)) {
            for (TransactionListener listener : list) context.attempt(listener::afterCommit);
            tasks.forEach(context::attempt);
            context.throwIfFailed();
        }
    }

    private void joinSpring(TransactionContext context) {
        if (context.springStatus != NO_SPRING_TRANSACTION || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                if (context.completed || context.rollbackOnly) return;
                if (readOnly && context.proxyChanges() != null && context.proxyChanges().hasDirty())
                    throw new IllegalStateException("只读事务不能执行自动更新");
                TransactionListenerManager.this.beforeCommit(context);
            }
            @Override public void afterCommit() {
                context.springStatus = STATUS_COMMITTED;
                notifyIfReady(context);
            }
            @Override public void afterCompletion(int status) {
                context.springStatus = status;
                if (status != STATUS_COMMITTED && CURRENT.get() == context) setRollbackOnly();
                notifyIfReady(context);
            }
        });
        context.springStatus = SPRING_PENDING;
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
