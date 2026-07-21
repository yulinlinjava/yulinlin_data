package com.yulinlin.data.core.wrapper.factory;

public interface AbstractWrapperFactory<E> {


    E create();

    E create(Object obj);



}
