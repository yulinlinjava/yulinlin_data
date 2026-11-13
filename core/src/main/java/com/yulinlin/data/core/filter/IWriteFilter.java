package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.ExecuteRequest;

/** 写入拦截：覆盖插入、更新和删除，不参与查询或阻止 selectEach。 */
public interface IWriteFilter extends IFilter {
    default ExecuteRequest<?> beforeWrite(String group, ExecuteRequest<?> request) { return request; }

    /** 实际执行完成通知，affectedRows 为影响行数；此时事务不一定已提交。 */
    default void afterWrite(String group, ExecuteRequest<?> request, int affectedRows) { }
}
