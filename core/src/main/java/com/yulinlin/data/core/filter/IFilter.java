package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;



/**
 * 对请求和返回值增强；同一个过滤器实例可能同时处理多个线程，不能保存请求级状态。
 */
public interface IFilter {

    default BaseRequest before(String session,BaseRequest request){

        return request;
    }

    default Object after(String session, BaseRequest request, Object data){
        return data;
    }





    default boolean test(String session,BaseRequest request){
        return true;
    }

    default int order(){
        return 0;
    }

}
