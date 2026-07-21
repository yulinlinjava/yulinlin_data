package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.model.BaseModelDeleteWrapper;
import com.yulinlin.data.core.wrapper.IConditionWrapper;

import java.lang.reflect.Method;
import java.util.*;

public class DeleteParseManager implements MethodParse{


    public static List<String> keys = Arrays.asList("deleteBy","removeBy");

    public void parseWhere(String name,Object[] args,IConditionWrapper conditionManager){
        for (String key : keys) {
            if(name.startsWith(key)){
                name = name.substring(key.length());
            }
        }


        WhereParseUtil.parseWhere(name,args,conditionManager);
    }

    @Override
    public Object apply(String name, Object[] args,Method method,Object obj) {

        Class clazz = WhereParseUtil.forMethodReturnType(method,obj);

        BaseModelDeleteWrapper wrapper = new BaseModelDeleteWrapper(null,clazz);
        IConditionWrapper where =(IConditionWrapper) wrapper.getWrapper().where();

            parseWhere(name,args,where);


        return wrapper.execute();
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
