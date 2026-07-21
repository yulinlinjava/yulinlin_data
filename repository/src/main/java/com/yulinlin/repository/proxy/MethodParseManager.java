package com.yulinlin.repository.proxy;

import java.lang.reflect.Method;
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
        for (MethodParse methodParse : methodParseList) {
            if(methodParse.support(name)){
                return methodParse.apply(name,args,method,obj);
            }
        }

        throw new RuntimeException("不支持解析");
    }

}
