package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.model.BaseModelUpdateWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 查询解析
 */
public class UpdateMethodParse implements MethodParse
{

    public static List<String> keys = Arrays.asList("update");

    public void parseWhere(String name,Object[] args,IConditionWrapper conditionManager){
        for (String key : keys) {
            if(name.startsWith(key)){
                name = name.substring(key.length());
            }
        }
        args =  Arrays.copyOfRange(args,1,args.length);

        WhereParseUtil.parseWhere(name,args,conditionManager);
    }


    @Override
    public Object apply(String name, Object[] args,Method method,Object obj) {
        Object arg = args[0];
        BaseModelUpdateWrapper wrapper;
        int total = 0;
        if(arg instanceof Collection){
            Collection coll = (Collection)arg;
            wrapper =new  BaseModelUpdateWrapper(null,coll);
        }else {
            wrapper =new BaseModelUpdateWrapper(null,arg);
        }
        IConditionWrapper where =(IConditionWrapper) wrapper.getWrapper().where();
        parseWhere(name,args,where);

        total =  wrapper.execute();

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
