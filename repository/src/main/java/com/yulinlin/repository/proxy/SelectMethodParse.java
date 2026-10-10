package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.model.BaseModelSelectWrapper;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.IConditionWrapper;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 查询解析
 */
public class SelectMethodParse implements MethodParse
{

    public static List<String> keys = Arrays.asList("selectBy","findBy","searchBy","find");
    private final ConcurrentMap<CacheOptionsKey, JoinCacheOptions> cacheOptions = new ConcurrentHashMap<>();

    public void parseWhere(String name,Object[] args,IConditionWrapper conditionManager){
        for (String key : keys) {
            if(name.startsWith(key)){
                name = name.substring(key.length());
            }
        }
        WhereParseUtil.parseWhere(name,args,conditionManager);
    }

    public void parseWhere(String name,Object[] args,BaseModelSelectWrapper wrapper) {
        if(args.length == 1 && args[0] instanceof IConditionWrapper){
            IConditionWrapper where =(IConditionWrapper) wrapper.getWrapper().where();
            where.and(((IConditionWrapper)args[0]));
        }else {
            IConditionWrapper where =(IConditionWrapper) wrapper.getWrapper().where();
            parseWhere(name,args,where);
        }

    }
        @Override
    public Object apply(String name, Object[] args, Method method, Object obj) {
        Class<?> repositoryType = repositoryType(obj, method);
            Class returnType =  method.getReturnType();
        Class clazz = WhereParseUtil.forMethodReturnType(method,obj);

            BaseModelSelectWrapper wrapper =new BaseModelSelectWrapper(null,clazz);

            parseWhere(name,args,wrapper);
            cacheOptions.computeIfAbsent(new CacheOptionsKey(repositoryType, method),
                    key -> JoinCacheOptions.from(key.repositoryType(), key.method())).apply(wrapper);

            if(List.class.isAssignableFrom(returnType)){
                return wrapper.selectList();
            }else {
                return wrapper.selectOne();
            }

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

    @Override
    public RequestType requestType() {
        return RequestType.select;
    }

    @Override
    public void validate(Class<?> repositoryType, Method method) {
        cacheOptions.computeIfAbsent(new CacheOptionsKey(repositoryType, method),
                key -> JoinCacheOptions.from(key.repositoryType(), key.method()));
    }

    private record CacheOptionsKey(Class<?> repositoryType, Method method) {
    }

    private static Class<?> repositoryType(Object proxy, Method method) {
        if (proxy != null) {
            for (Class<?> candidate : proxy.getClass().getInterfaces()) {
                if (method.getDeclaringClass().isAssignableFrom(candidate)) return candidate;
            }
        }
        return method.getDeclaringClass();
    }
}
