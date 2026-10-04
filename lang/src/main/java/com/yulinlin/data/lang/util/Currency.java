package com.yulinlin.data.lang.util;

import java.util.function.Function;

//做数据库小数计算用的
public abstract class Currency<E>  extends Number implements CodeObject<Long>{

    private E value;

    public Currency() {
    }

    public Currency(E value) {
        this.value = value;
    }

    public E getValue() {
        return value;
    }

    public abstract Currency<E> apply(Function<E,E> func);


    //小数精度
    public abstract int decimal();




    public int decimalSize(){
        int size = 1;
        for(int x = 0;x<decimal();x++){
            size*=10;
        }
        return size;
    }



    public void setValue(E value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value+"";
    }
}
