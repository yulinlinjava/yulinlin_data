package com.yulinlin.data.core.log;

import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.lang.reflection.GenericUtil;

//日志打印对象
//E 是请求对象
public interface LogPrint<E> {

    //执行成功
    void success(long time,ParseResult request);

    //执行失败
    void error(Throwable e,  ParseResult request);

     default boolean isHandle( ParseResult result){
         Class<?> generic = GenericUtil.getGeneric(this.getClass(), LogPrint.class, 0);
         Class<?> aClass = result.getRequest().getClass();

         return generic == aClass ||  generic.isAssignableFrom( aClass);
    }


}
