package com.yulinlin.data.core.transaction;

import com.yulinlin.data.core.session.SessionUtil;

import java.util.Objects;

/** 事务业务回调入口；回调在提交成功后同步执行。 */
public class TransactionUtil {
    /**
     * 注册到当前框架事务；最外层提交成功并清理旧上下文后同步执行。
     * 没有框架事务时直接报错，不单独接管纯 Spring 事务。
     */
    public static void afterCommit(Runnable action) {
        Objects.requireNonNull(action, "action");
        manager().afterCommit(action);
    }

    /** 只使用已开启的路由事务，或已配置的 RouteSession；不创建独立 Session 事务。 */
    public static TransactionListenerManager manager() {
        TransactionContext context = TransactionListenerManager.currentContext();
        if (context != null) return context.manager();
        var route = SessionUtil.route();
        if (route == null) throw new IllegalStateException("请先配置 RouteSession，并通过路由入口开启事务");
        return route.getTransactionListenerManager();
    }
}
