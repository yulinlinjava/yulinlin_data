package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.session.RequestType;

import java.util.List;


/**
 * 根据请求分类
 */
public interface IRequestFilter  extends IFilter{

    @Override
    default BaseRequest before(String session, BaseRequest request) {
        if(request instanceof ExecuteRequest){

            return updateBefore(session,(ExecuteRequest)request);
        }
        if(request instanceof  QueryRequest){
            return selectBefore(session,(QueryRequest)request);
        }
        return request;
    }


    @Override
    default Object after(String session, BaseRequest request, Object data) {
        if(request instanceof ExecuteRequest){
            return updateAfter(session,request,data);
        }
        if(request instanceof QueryRequest) {
            if(data instanceof Number){
                return data;
            }
            return selectAfter(session, request, (List) data);

        }
        return data;
    }

    //查询监听
    default QueryRequest selectBefore(String session, QueryRequest<?> request){

        return request;
    }

    //查询监听
    default Object selectAfter(String session, BaseRequest<?> request, List<Object> coll){
        return coll;
    }


    //更新监听
    default ExecuteRequest updateBefore(String session, ExecuteRequest<?> request){
        return request;
    }


    //更新监听
    default Object updateAfter(String session, BaseRequest<?> request,  Object data){
return data;
    }


}
