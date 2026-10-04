package com.yulinlin.data.core.parse;


import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.IWrapper;


public class SimpParamsContext implements IParamsContext {

    private IDataBuffer dataBuffer;

    private int i = 0;

    private Object root;

    AliasContent aliasContent;

    RequestType requestType;


    public SimpParamsContext(
            RequestType requestType,
            Object root,
                             IDataBuffer dataBuffer,
                             Class clazz,
                             boolean mapUnderscoreToCamelCase) {

        this.requestType = requestType;
        this.root = root;
        this.dataBuffer = dataBuffer;
        this.aliasContent =AliasContent.newInstance(clazz,mapUnderscoreToCamelCase);



    }

    public Object getRoot() {
        return root;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    @Override
    public String putGetKey(Object value) {
        String key=""+i++;
        dataBuffer.put(key,value);
        return "#{"+key+"}";
    }

    @Override
    public String toColumn(String name) {
        return aliasContent.toColumn(name);
    }

    @Override
    public AliasContent getAliasContent() {
        return aliasContent;
    }

    @Override
    public String toKey(String name) {
        return aliasContent.toKey(name);
    }

    @Override
    public void put(String key, Object value) {
        dataBuffer.put(key,value);
    }

    public IDataBuffer getDataBuffer() {
        return dataBuffer;
    }


}
