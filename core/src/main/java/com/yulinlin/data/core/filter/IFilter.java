package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;

/**
 * 过滤器公共匹配和排序契约。业务实现 IQueryFilter / IWriteFilter。
 * 同一个实例可能同时处理多个线程，不能保存请求级状态。
 */
public interface IFilter {
    default boolean test(String session,BaseRequest request){
        return true;
    }

    default int order(){
        return 0;
    }

}
