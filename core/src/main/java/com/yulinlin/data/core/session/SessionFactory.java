package com.yulinlin.data.core.session;

public interface SessionFactory<E> {


    EntitySession create(E client,String name);


}
