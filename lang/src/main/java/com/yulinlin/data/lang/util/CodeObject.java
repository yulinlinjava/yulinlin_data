package com.yulinlin.data.lang.util;

public interface CodeObject<E> {


    E encode();

    void decode(Object value);



}
