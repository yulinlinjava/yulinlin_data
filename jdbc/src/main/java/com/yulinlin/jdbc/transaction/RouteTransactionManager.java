package com.yulinlin.jdbc.transaction;

import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.transaction.TransactionContext;
import com.yulinlin.data.core.transaction.TransactionListenerManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Objects;
import java.util.function.Supplier;

/** 仅适配 Spring 注解协议；连接、嵌套、提交回调全部由 RouteSession 管理。 */
public final class RouteTransactionManager implements PlatformTransactionManager {
    private final Supplier<RouteSession> routes;

    public RouteTransactionManager(Supplier<RouteSession> routes) {
        this.routes = Objects.requireNonNull(routes, "routes");
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        if (definition != null) validate(definition);
        RouteSession route = Objects.requireNonNull(routes.get(), "routeSession");
        boolean newTransaction = !route.isOpenTransaction();
        route.startTransaction();
        return new Status(this, route, TransactionListenerManager.currentContext(), newTransaction);
    }

    private static void validate(TransactionDefinition definition) {
        if (definition.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED)
            throw new IllegalTransactionStateException("yulinlin-data 仅支持 REQUIRED 传播，不支持挂起、独立事务或保存点");
        if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT)
            throw new IllegalTransactionStateException("yulinlin-data 暂不支持注解指定事务隔离级别，请使用数据源默认值");
        if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT)
            throw new IllegalTransactionStateException("yulinlin-data 暂不支持注解指定事务超时");
        if (definition.isReadOnly())
            throw new IllegalTransactionStateException("yulinlin-data 暂不支持 readOnly=true 的只读事务语义");
    }

    @Override
    public void commit(TransactionStatus transaction) {
        Status status = status(transaction);
        try {
            if (status.isLocalRollbackOnly()) {
                status.route.rollbackTransaction();
            } else if (status.isNewTransaction() && status.isGlobalRollbackOnly()) {
                status.route.rollbackTransaction();
                throw new UnexpectedRollbackException("事务已标记 rollback-only，全部修改已回滚");
            } else {
                status.route.commitTransaction();
            }
        } finally {
            // 提交后回调也可能抛异常；资源已经结束时不能再次提交或回滚。
            status.setCompleted();
        }
    }

    @Override
    public void rollback(TransactionStatus transaction) {
        Status status = status(transaction);
        try {
            status.route.rollbackTransaction();
        } finally {
            status.setCompleted();
        }
    }

    private Status status(TransactionStatus transaction) {
        if (!(transaction instanceof Status status) || status.manager != this)
            throw new IllegalTransactionStateException("事务状态不属于当前 yulinlin-data 事务适配器");
        status.check();
        if (status.context.depth() != status.depth)
            throw new IllegalTransactionStateException("事务必须按嵌套顺序结束，请检查手动 start/commit/rollback 是否配对");
        return status;
    }

    private static final class Status extends SimpleTransactionStatus {
        final RouteTransactionManager manager;
        final RouteSession route;
        final TransactionContext context;
        final int depth;

        Status(RouteTransactionManager manager, RouteSession route, TransactionContext context, boolean newTransaction) {
            super(newTransaction);
            this.manager = manager;
            this.route = route;
            this.context = Objects.requireNonNull(context, "transactionContext");
            this.depth = context.depth();
        }

        void check() {
            if (isCompleted() || TransactionListenerManager.currentContext() != context)
                throw new IllegalTransactionStateException("事务已结束或不属于当前线程，不能重复操作");
        }

        @Override
        public void setRollbackOnly() {
            check();
            super.setRollbackOnly();
            route.setRollbackOnly();
        }

        @Override
        public boolean isGlobalRollbackOnly() {
            return TransactionListenerManager.currentContext() == context && route.isRollbackOnly();
        }
    }
}
