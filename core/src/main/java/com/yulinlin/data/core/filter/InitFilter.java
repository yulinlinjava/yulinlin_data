package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.event.IInitEvent;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.request.BaseRequest;
import com.yulinlin.data.core.session.EntitySession;

import java.util.List;

public class InitFilter implements IRequestFilter {


    @Override
    public Object selectAfter(String session, BaseRequest<?> request, List<Object> coll) {
        if(coll.isEmpty()){
            return coll;
        }
        if(IInitEvent.class.isAssignableFrom( coll.get(0).getClass())){
            for (Object o : coll) {
                ((IInitEvent) o).init();
            }
        }
        return coll;
    }

    @Override
    public int order() {
        return 9999;
    }
}
