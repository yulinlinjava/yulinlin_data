package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;

/** 由 RouteSession 统一调用；具体 Session 不执行过滤器。 */
public interface IFilterManager {
    IFilterManager NONE = new IFilterManager() { };
    /**
     * SQL 解析和缓存 Key 构造之前增强请求，允许改表名、条件和路由，或返回同类新请求。
     */
    default BaseRequest before(String session,BaseRequest request){

        return request;
    }

    /** 后端执行或缓存命中后增强返回值，不等于事务提交成功；分页收到整个 Page。 */
    default Object after(String session, BaseRequest request, Object data){
        return data;
    }


}
