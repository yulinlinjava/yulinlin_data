package com.yulinlin.data.core.parse;


import com.yulinlin.data.core.alias.AliasContent;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.IWrapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;


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
        Objects.requireNonNull(value, "value");
        Number scalar = arithmeticScalar(value);
        if (scalar == null) {
            // Money/Coin 等 Number 仍需按编码器转换存储单位，原子类也需读取值快照。
            scalar = arithmeticScalar(dataBuffer.encode(value));
            if (scalar == null) {
                throw new IllegalArgumentException("算术参数编码后必须是 JDBC 支持的数字类型："
                        + value.getClass().getName());
            }
        }
        String key = "" + i++;
        // 只绕过原生数字的存储编码，避免 BigDecimal 被转成文本或金额被重复缩放。
        dataBuffer.toMap().put(key, scalar);
        return "#{" + key + "}";
    }

    private static Number arithmeticScalar(Object value) {
        if (value == null) return null;
        Class<?> type = value.getClass();
        if (type == BigInteger.class) return new BigDecimal((BigInteger) value);
        if (type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == Float.class || type == Double.class || type == BigDecimal.class) {
            return (Number) value;
        }
        return null;
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
