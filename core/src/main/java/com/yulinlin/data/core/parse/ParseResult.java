package com.yulinlin.data.core.parse;

import com.yulinlin.data.core.cache.CacheKey;

public class ParseResult {

    private ParseType type;

    private Object request;

    private IParamsContext context;

    public ParseResult(ParseType type, Object request, IParamsContext context) {
        this.type = type;
        this.request = request;
        this.context = context;
    }

    public IParamsContext getContext() {
        return context;
    }

    public ParseType getType() {
        return type;
    }

    public Object getRequest() {
        return request;
    }


}
