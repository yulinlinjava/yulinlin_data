package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;

public interface IFilterManager {
    /**
     * 运行前增强
     * @param session
     * @param request
     * @return
     */
    default BaseRequest before(String session,BaseRequest request){

        return request;
    }

    //查询结果出来
    default Object after(String session, BaseRequest request, Object data){
        return data;
    }


}
