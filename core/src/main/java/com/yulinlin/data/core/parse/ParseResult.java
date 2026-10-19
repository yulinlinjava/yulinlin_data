package com.yulinlin.data.core.parse;

import com.yulinlin.data.core.cache.CacheKey;

public class ParseResult {

    private ParseType type;

    private Object request;

    private IParamsContext context;

    /** 执行前在所属线程捕获，仅用于日志关联，不传播业务事务。 */
    private String transactionId;

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

    public String getTransactionId() { return transactionId; }

    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }


}
