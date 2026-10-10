package com.yulinlin.repository.proxy;

import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.repository.anno.JoinCache;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class MethodParseManager {

    private List<MethodParse> methodParseList;

    public MethodParseManager() {
        this.methodParseList = new ArrayList<>();
        init();
    }

    private void init(){

        register(new SelectMethodParse());
        register(new InsertMethodParse());
        register(new DeleteParseManager());
        register(new UpdateMethodParse());
    }


    public void register(MethodParse parse){
        methodParseList.add(parse);
    }

    public Object apply(String name, Object[] args, Method method,Object obj){
        return resolve(name).apply(name,args,method,obj);
    }

    public void validate(Class<?> repositoryType) {
        for (Method method : repositoryType.getMethods()) {
            if (method.getAnnotation(JoinCache.class) == null) continue;
            if (!Modifier.isAbstract(method.getModifiers())) {
                throw new IllegalArgumentException("@JoinCache only supports abstract Repository query methods: "
                        + method.toGenericString());
            }
            MethodParse parser = resolve(method.getName());
            if (parser.requestType() != RequestType.select) {
                throw new IllegalArgumentException("@JoinCache cannot be used on Repository write method: "
                        + method.toGenericString());
            }
            parser.validate(method);
        }
    }

    private MethodParse resolve(String name) {
        for (MethodParse methodParse : methodParseList) {
            if (methodParse.support(name)) return methodParse;
        }
        throw new IllegalArgumentException("不支持解析Repository方法: " + name);
    }

}
