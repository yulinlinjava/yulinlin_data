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

    private final Class<?> sourceClass;


    public SimpParamsContext(
            RequestType requestType,
            Object root,
                             IDataBuffer dataBuffer,
                             Class clazz,
                             boolean mapUnderscoreToCamelCase) {

        this.requestType = requestType;
        this.root = root;
        this.dataBuffer = dataBuffer;
        this.sourceClass = clazz == null ? Object.class : clazz;
        this.aliasContent =AliasContent.newInstance(clazz,mapUnderscoreToCamelCase);



    }

    public Object getRoot() {
        return root;
    }

    @Override
    public Class<?> getSourceClass() {
        return sourceClass;
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
    public String putNumberGetKey(Number value) {
        String key = "" + i++;
        Number scalar = value instanceof java.math.BigInteger integer
                ? new java.math.BigDecimal(integer) : java.util.Objects.requireNonNull(value, "value");
        // 数字已经是数据库算术标量，不能再次经过字段存储编码器。
        dataBuffer.toMap().put(key, scalar);
        return "#{" + key + "}";
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
