package com.yulinlin.data.core.wrapper;

import com.yulinlin.data.lang.lambda.LambdaPropertyFunction;

//存储接口
public interface ISortWrapper<E,R extends ISortWrapper<E,R>> {


    default R orderByAsc(String name){
        return orderBy(name,true);
    }

    default R orderByDesc(String name){
        return orderBy(name,false);
    }


    R orderBy(String name, boolean asc);

    R orderBy(LambdaPropertyFunction<E> name, boolean asc) ;

    R orderByDesc(LambdaPropertyFunction<E> name) ;

    R orderByAsc(LambdaPropertyFunction<E> name) ;

}
