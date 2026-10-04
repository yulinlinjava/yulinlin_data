package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.model.BaseModelInsertWrapper;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 查询解析
 */
public class InsertMethodParse implements MethodParse
{

    public static List<String> keys = Arrays.asList("insert");

    @Override
    public Object apply(String name, Object[] args,Method method,Object obj) {
        int total = 0;
        for (Object arg : args) {
            if(arg instanceof Collection){
                Collection coll = (Collection)arg;
                total+=new  BaseModelInsertWrapper(null,coll).execute();
            }else {
                total+=new BaseModelInsertWrapper(null,arg).execute();
            }
        }
        return total;
    }

    @Override
    public boolean support(String name) {
        for (String key : keys) {
            if(name.startsWith(key)){
                return true;
            }
        }
        return false;
    }
}
