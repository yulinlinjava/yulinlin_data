package com.yulinlin.data.core.session;

import com.yulinlin.data.core.loadbalan.LoadBalanceNode;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.lang.util.Page;

import java.util.Collection;
import java.util.List;

/** 数据源执行与资源协议；业务 CRUD 和事务必须通过 RouteSession，不作为独立业务入口。 */
public interface EntitySession extends LoadBalanceNode,TransactionSession {

    /** 是否支持由 RouteSession 管理的本地物理事务。 */
    default boolean supportsTransactions() { return false; }
    default Object createSavepoint() { throw new UnsupportedOperationException("当前数据源不支持保存点"); }
    default void releaseSavepoint(Object savepoint) { throw new UnsupportedOperationException("当前数据源不支持保存点"); }
    default void rollbackToSavepoint(Object savepoint) { throw new UnsupportedOperationException("当前数据源不支持保存点"); }

    /** Whether this session can use independent connections for concurrent writes in the current context. */
    default boolean supportsParallelWrites() {
        return false;
    }

    /** Generates this session's table/index initialization statements without executing them. */
    List<String> createTableSql(Class<?> entityClass);

    /** Initializes the supplied schema-owner entities using this session. */
    void initializeSchema(Collection<Class<?>> entityClasses);


    <E> Integer insert(ExecuteRequest<E> request );

    <E> Integer  update(ExecuteRequest<E> request );

    <E> Integer  delete(ExecuteRequest<E> request );

    <E> Page<E> page(QueryRequest<E> request );

    <E> List<E> select(QueryRequest<E> request );

    <E> Integer count(QueryRequest<E> request );

    <E> List<E> group(QueryRequest<E> request );

    /** Diagnostics are opt-in; unsupported backends must never execute the original query instead. */
    default List<java.util.Map<String, Object>> explain(QueryRequest<?> request) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 不支持 explain");
    }

    default List<java.util.Map<String, Object>> explainCount(QueryRequest<?> request) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 不支持 explainCount");
    }


}
