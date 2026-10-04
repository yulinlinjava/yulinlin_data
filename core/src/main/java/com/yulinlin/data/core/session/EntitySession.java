package com.yulinlin.data.core.session;

import com.yulinlin.data.core.loadbalan.LoadBalanceNode;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.lang.util.Page;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EntitySession extends LoadBalanceNode,TransactionSession {



    <E> Integer insert(ExecuteRequest<E> request );

    <E> Integer  update(ExecuteRequest<E> request );

    <E> Integer  delete(ExecuteRequest<E> request );

    <E> Page<E> page(QueryRequest<E> request );

    <E> List<E> select(QueryRequest<E> request );

    <E> Integer count(QueryRequest<E> request );

    <E> List<E> group(QueryRequest<E> request );


}
