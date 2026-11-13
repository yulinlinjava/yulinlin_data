package com.yulinlin.data.core.filter;

import com.yulinlin.data.core.event.IInitEvent;
import com.yulinlin.data.core.request.QueryRequest;

public class InitFilter implements IQueryFilter {

    @Override
    public void afterRow(String session, QueryRequest<?> request, Object row) {
        if (row instanceof IInitEvent event) event.init();
    }

    @Override
    public int order() {
        return 9999;
    }
}
