package com.yulinlin.data.core.coder;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public interface IDataBuffer {


     Set<String> keys();

     Object encode(Object value);

     Object decode(Class clazz);

     Object decode(Object value, Class clazz) ;

    Object decode(Object value,Field clazz);

     <E> E getObject(String key,Class clazz);

    <E> E getObject(String key,Field clazz);

     <E> E getObject(String key);



     Map<String,Object> toMap();

     IDataBuffer put(String key,Object val);

     IDataBuffer putAll(Map<String,Object> map);

    IDataBuffer copy();


    void clear();

}
