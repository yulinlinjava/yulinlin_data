package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.lang.util.Page;

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
            if (data instanceof Page<?> page) {
                Object value = selectAfter(session, request, (List) page.getList());
                if (value == page.getList()) return page;
                if (!(value instanceof List<?> rows))
                    throw new IllegalStateException("分页 selectAfter 必须返回 List");
                Page<?> replacement = new Page<>(rows, page.getTotal());
                if (page.getExtra() != null) page.getExtra().forEach(replacement::putValue);
                return replacement;
            }
            if (data instanceof List) return selectAfter(session, request, (List) data);
            // count/null 不属于列表结果；普通 IFilter.after 仍可单独处理统计值。
            return data;

        }
        return data;
    }

    //查询监听
    default QueryRequest selectBefore(String session, QueryRequest<?> request){

        return request;
    }

    /** select/group 的列表或分页中的列表；替换分页列表时保留 total 与 extra。 */
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
