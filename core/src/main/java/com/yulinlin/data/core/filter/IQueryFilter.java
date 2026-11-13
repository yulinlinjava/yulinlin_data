package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.QueryRequest;

/** 查询拦截：SQL 解析前修改请求，解码后逐行增强对象。 */
public interface IQueryFilter extends IFilter {
    default QueryRequest<?> beforeQuery(String group, QueryRequest<?> request) { return request; }

    /** 原地增强单行；列表、分页、分组及 selectEach 共用，不修改行数或分页 total。 */
    default void afterRow(String group, QueryRequest<?> request, Object row) { }

    /** 依赖关联加载或需要在 afterRow 中查询数据库时，可显式拒绝流式入口。 */
    default boolean supportsSelectEach() { return true; }
}
