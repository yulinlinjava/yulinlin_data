package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;

/** 只由 RouteSession 调用；具体 EntitySession 不依赖过滤器。 */
public interface IFilterManager {
    IFilterManager NONE = new IFilterManager() { };

    default QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) {
        return request;
    }

    default ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) {
        return request;
    }

    default void afterRow(String group, QueryRequest<?> request, Object row) { }

    /** 实际执行完成通知，不改变影响行数，也不代表事务已提交。 */
    default void afterWrite(String group, ExecuteRequest<?> request, int affectedRows) { }

    /** 在读取前检查流式支持；依赖关联或额外 SQL 的自定义管理器可覆盖并拒绝。 */
    default void checkSelectEach(String group, QueryRequest<?> request) { }
}
