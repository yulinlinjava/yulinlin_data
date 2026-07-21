package com.yulinlin.data.core.parse;

import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.core.node.INode;

import java.util.*;

public abstract class SimpParseManager implements IParseManager {

    public Map<Class,IParse> parseMap;



    public SimpParseManager() {
        this.parseMap = new HashMap<>();


        this.init();
    }

    protected abstract void init();

    public void register(IParse parse) {
        Class clazz =  parse.getNodeClass();
        parseMap.put(clazz,parse);
    }

    public void register(List<IParse> list) {
        for (IParse iParse : list) {
            register(iParse);
        }
    }

    public Object parse(INode condition, IParamsContext list){
        if(condition == null){
            return null;
        }

        IParse iParse =   parseMap.get(condition.getClass());
        if(iParse == null){
            throw new NoticeException("解析器不存在:"+condition.getClass());
        }
        return  iParse.parse(condition,list,this);
    }



}
