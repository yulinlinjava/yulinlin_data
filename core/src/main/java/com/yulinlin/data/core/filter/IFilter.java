package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.request.BaseRequest;

import com.yulinlin.data.core.session.EntitySession;


/**
 * 对请求增强
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
