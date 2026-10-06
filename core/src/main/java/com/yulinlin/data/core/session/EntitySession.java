package com.yulinlin.data.core.session;

import com.yulinlin.data.core.loadbalan.LoadBalanceNode;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.lang.util.Page;

import java.util.Collection;
import java.util.List;

public interface EntitySession extends LoadBalanceNode,TransactionSession {

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


}
