package com.yulinlin.data.lang.tuple;

public class MutablePair<K,V>  extends Triple{


    public MutablePair(Object... values) {
        super(values);
    }

    public K getLeft(){
        return get(0);
    }


    public V getRight(){
        return get(1);
    }

    public static <K,V> MutablePair<K,V> of(K k,V v){
        return new MutablePair(k,v);
    }
}
