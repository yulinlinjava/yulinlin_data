package com.yulinlin.data.lang.event;


import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.util.GenericClass;

public interface IEventHandler<E> {

    void handle(E event);




    default Class<?> getEventClass(){
        return GenericUtil.getGeneric(this.getClass(),IEventHandler.class,0);

    }
}
