/*
package com.yulinlin.admin;

import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.data.core.filter.IRequestFilter;
import com.yulinlin.data.core.model.ModelConditionWrapper;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.IConditionWrapper;
import com.yulinlin.data.core.wrapper.IDeleteWrapper;
import org.springframework.stereotype.Component;

@Component
public class DeleteFilter implements IRequestFilter {

    @Override
    public ExecuteRequest updateBefore(String session, ExecuteRequest<?> request) {
        if(request.getRequestType() == RequestType.delete && request.getFromClass() == SysUserEntity.class){
            IDeleteWrapper deleteWrapper = (IDeleteWrapper) request.getWrappers().get(0);
            IConditionWrapper where = (IConditionWrapper)deleteWrapper.where();
            ExecuteRequest req = ModelUpdateWrapper.newInstance(request.getFromClass())
                    .field("status", 1)
                    .and(where).getRequest();
            return req;
        }

        return request;
    }


}
*/
